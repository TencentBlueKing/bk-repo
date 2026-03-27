package com.tencent.bkrepo.job.batch.task.drive

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.hash.BloomFilter
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.COUNT
import com.tencent.bkrepo.job.CREDENTIALS
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.FileJobContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.config.properties.DriveFileReferenceCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * 清理 drive_file_reference 中引用<=0的文件
 */
@Component
@Suppress("UnstableApiUsage")
class DriveFileReferenceCleanupJob(
    private val storageService: StorageService,
    private val storageCredentialService: StorageCredentialService,
    private val properties: DriveFileReferenceCleanupJobProperties,
    @Qualifier("driveMongoTemplate")
    private val driveMongoTemplate: MongoTemplate,
) : MongoDbBatchJob<DriveFileReferenceCleanupJob.FileReferenceData, FileJobContext>(properties) {

    private lateinit var driveBlockNodeBf: BloomFilter<CharSequence>
    private lateinit var ignoredCredentialsKeys: Set<String?>
    private val credentialsCache: LoadingCache<String, StorageCredentials> = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build(CacheLoader.from { key -> storageCredentialService.findByKey(key) })

    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)

    override fun createJobContext(): FileJobContext {
        driveBlockNodeBf = NodeCommonUtils.buildBlockNodeBloomFilter(
            collectionName = COLLECTION_DRIVE_BLOCK_NODE_PREFIX,
            shardingCount = SHARDING_COUNT,
            expectedInsertions = properties.expectedBlockNodes,
            fpp = properties.fpp,
            mongoTemplate = driveMongoTemplate
        )
        ignoredCredentialsKeys = properties.ignoredStorageCredentialsKeys.mapTo(mutableSetOf()) { key ->
            if (key == DEFAULT_STORAGE_KEY) null else key
        }
        return FileJobContext()
    }

    override fun entityClass(): KClass<FileReferenceData> {
        return FileReferenceData::class
    }

    override fun batchQueryMongoTemplate(): MongoTemplate = driveMongoTemplate

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "$COLLECTION_DRIVE_FILE_REFERENCE_PREFIX$it" }.toList()
    }

    override fun buildQuery(): Query {
        return Query(Criteria.where(COUNT).lte(0))
    }

    override fun run(row: FileReferenceData, collectionName: String, context: FileJobContext) {
        if (ignoredCredentialsKeys.contains(row.credentialsKey)) {
            return
        }
        val credentialsKey = row.credentialsKey
        val sha256 = row.sha256
        val id = row.id
        val storageCredentials = credentialsKey?.let { credentialsCache.get(it) }
        try {
            if (existDriveBlockNode(sha256)) {
                logger.warn(
                    "Deletion refused, drive file[{}] on [{}] still has drive block references.",
                    sha256,
                    credentialsKey
                )
                correctRefCount(sha256, credentialsKey, collectionName)
                return
            }

            var successToDeleted = false
            if (storageService.exist(sha256, storageCredentials)) {
                storageService.delete(sha256, storageCredentials)
                successToDeleted = true
            }
            if (!successToDeleted) {
                context.fileMissing.incrementAndGet()
                logger.warn(
                    "Drive file[{}] is missing on [{}], skip cleaning up.",
                    sha256,
                    storageCredentials?.key
                )
            }
            driveMongoTemplate.remove(Query(Criteria(ID).isEqualTo(id)), collectionName)
        } catch (e: Exception) {
            throw JobExecuteException("Failed to delete drive file[$sha256] on [${storageCredentials?.key}].", e)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): FileReferenceData {
        return FileReferenceData(row)
    }

    private fun correctRefCount(sha256: String, credentialsKey: String?, collectionName: String) {
        val criteria = Criteria.where(SHA256).isEqualTo(sha256)
            .and(CREDENTIALS).isEqualTo(credentialsKey)
            .and(COUNT).lte(0)
        val query = Query.query(criteria)
        val result = driveMongoTemplate.updateFirst(query, Update().set(COUNT, 1L), collectionName)
        if (result.modifiedCount == 1L) {
            logger.info("Success to correct drive reference[{}] on [{}].", sha256, credentialsKey)
        }
    }

    private fun existDriveBlockNode(sha256: String): Boolean {
        if (!driveBlockNodeBf.mightContain(sha256)) {
            return false
        }
        val query = Query(Criteria.where(SHA256).isEqualTo(sha256)).limit(1)
        for (i in 0 until SHARDING_COUNT) {
            if (driveMongoTemplate.exists(query, "${COLLECTION_DRIVE_BLOCK_NODE_PREFIX}_$i")) {
                return true
            }
        }
        return false
    }

    data class FileReferenceData(private val map: Map<String, Any?>) {
        val id: String by map
        val sha256: String by map
        val credentialsKey: String? = map[CREDENTIALS] as String?
    }

    companion object {
        private val logger = LoggerHolder.jobLogger

        private const val COLLECTION_DRIVE_FILE_REFERENCE_PREFIX = "drive_file_reference_"
        private const val COLLECTION_DRIVE_BLOCK_NODE_PREFIX = "drive_block_node"
    }
}
