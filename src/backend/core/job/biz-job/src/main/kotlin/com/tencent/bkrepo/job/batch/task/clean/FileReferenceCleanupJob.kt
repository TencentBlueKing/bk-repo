/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.task.clean

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.constant.DEFAULT_KEY
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.COUNT
import com.tencent.bkrepo.job.CREDENTIALS
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.FileJobContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.config.properties.FileReferenceCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.job.exception.RepoMigratingException
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 清理引用=0的文件
 */
@Component
@EnableConfigurationProperties(FileReferenceCleanupJobProperties::class)
@Suppress("UnstableApiUsage")
class FileReferenceCleanupJob(
    private val storageService: StorageService,
    private val storageCredentialService: StorageCredentialService,
    private val properties: FileReferenceCleanupJobProperties,
    private val archiveClient: ArchiveClient,
) : MongoDbBatchJob<FileReferenceCleanupJob.FileReferenceData, FileJobContext>(properties) {

    /**
     * 节点的布隆过滤器，用于快速判断sha256的节点是否存在
     * 因跟踪节点的删除不方便，且布隆过滤器没有重置功能，所以这里每次任务开始前都会新建一个布隆过滤器，
     * 如果设置的预期节点很多，可能会导致较多的gc甚至oom。
     * */
    private lateinit var bf: BloomFilter<CharSequence>

    /**
     *
     * 两个credentials使用相同的存储时（例如同一个对象存储桶），可能导致数据误删，
     * 例如存储迁移的场景，迁移前后的存储桶一样仅缓存路径改变的情况
     * 此时需要获取相同存储的映射关系，避免迁移结束旧存储引用减到0后将后端存储的数据删除，导致数据丢失
     *
     * 设置映射关系后会检查映射的StorageCredentialsKey是否存在对应引用，存在时将不删实际存储文件仅删除存储自身的引用
     */
    @Volatile
    private lateinit var storageKeyMapping: Map<String, Set<String>>

    override fun createJobContext(): FileJobContext {
        bf = buildBloomFilter()
        storageKeyMapping = storageCredentialService.getStorageKeyMapping()
        logger.info("storage key mapping: [$storageKeyMapping]")
        return FileJobContext()
    }

    override fun entityClass(): KClass<FileReferenceData> {
        return FileReferenceData::class
    }

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT)
            .map { "$COLLECTION_NAME_PREFIX$it" }
            .toList()
    }

    override fun buildQuery(): Query {
        val ignores: MutableSet<String?> = properties.ignoredStorageCredentialsKeys.toMutableSet()
        if (ignores.contains(DEFAULT_KEY)) {
            ignores.remove(DEFAULT_KEY)
            ignores.add(null)
        }
        // 可能存在历史脏数据引用数为负数，此处需要查询<=0的数据
        return Query(
            Criteria.where(COUNT).lte(0)
                .and(CREDENTIALS).not().inValues(ignores),
        )
    }

    override fun run(row: FileReferenceData, collectionName: String, context: FileJobContext) {
        val credentialsKey = row.credentialsKey
        val sha256 = row.sha256
        val id = row.id
        val storageCredentials = credentialsKey?.let { getCredentials(credentialsKey) }
        try {
            /*
            * 我们认为大部分的情况下，引用计数应该是正确的，并且为了确保文件没有被节点或者压缩root等资源引用
            * 我们需要进行强制判断，同时文件丢失的情况，我们认为也是小概率事件，因此我们选择先进行文件是否可以
            * 被删除的判断，再来决定资源的删除问题。
            * */
            if (existNode(sha256, credentialsKey) || isGcBase(sha256, credentialsKey)) {
                logger.warn("Deletion refused, file[$sha256] on [$credentialsKey] has references.")
                if (!properties.dryRun) {
                    // 存在节点，则表明引用至少需要为1。
                    correctRefCount(sha256, credentialsKey, collectionName)
                }
                return
            }
            if (properties.dryRun) {
                logger.info("Mock delete $sha256 on $credentialsKey.")
                return
            }
            var successToDeleted = cleanupRelatedResources(sha256, credentialsKey)
            val existsRefOfMappingStorage = existsRefOfMappingStorage(row, collectionName)
            if (!existsRefOfMappingStorage && storageService.exist(sha256, storageCredentials)) {
                storageService.delete(sha256, storageCredentials)
                successToDeleted = true
            }
            if (!successToDeleted) {
                context.fileMissing.incrementAndGet()
                logger.warn(
                    "File[$sha256] is missing on [${storageCredentials?.key}] or " +
                            "existsRefOfMappingStorage[$existsRefOfMappingStorage], skip cleaning up."
                )
            }
            mongoTemplate.remove(Query(Criteria(ID).isEqualTo(id)), collectionName)
        } catch (e: Exception) {
            if (e is RepoMigratingException) {
                logger.info(e.message)
            } else {
                throw JobExecuteException("Failed to delete file[$sha256] on [${storageCredentials?.key}].", e)
            }
        }
    }

    private fun correctRefCount(sha256: String, credentialsKey: String?, collectionName: String) {
        val criteria = Criteria.where(SHA256).isEqualTo(sha256)
            .and(CREDENTIALS).isEqualTo(credentialsKey)
            .and(COUNT).lte(0)
        val query = Query.query(criteria)
        val update = Update().set(COUNT, 1L)
        val result = mongoTemplate.updateFirst(query, update, collectionName)
        if (result.modifiedCount == 1L) {
            logger.info("Success to correct reference[$sha256] on [$credentialsKey].")
        } else {
            logger.info("Skip correct reference[$sha256].")
        }
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)

    /**
     * 检查Node表中是否还存在对应sha256的node
     * @return true表示存在节点，false表示不存在
     */
    private fun existNode(sha256: String, key: String?): Boolean {
        /*
        * 1. 通过布隆过滤器，快速判断节点是否存在。（大部分判断应该在这里终止，即大部分引用是正确的）
        * 2. 真实判断存储实例的节点是否存在。（引用不正确的情况或者布隆过滤器的误报）
        * */
        val query = Query(where(Node::sha256).isEqualTo(sha256))
        val mightContain = bf.mightContain(sha256)
        if (mightContain) {
            logger.info("Bloom filter might contain $sha256.")
        }
        return mightContain && NodeCommonUtils.exist(query, key)
    }

    private fun buildBloomFilter(): BloomFilter<CharSequence> {
        logger.info("Start build bloom filter.")
        val bf = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            properties.expectedNodes,
            properties.fpp,
        )
        val query = Query(Criteria.where(FOLDER).isEqualTo(false))
        query.fields().include(SHA256)
        NodeCommonUtils.forEachNodeByCollectionParallel(query) {
            val sha256 = it[SHA256]?.toString()
            if (sha256 != null) {
                bf.put(sha256)
            }
        }

        //加上冷表检查
        NodeCommonUtils.forEachColdNodeByCollectionParallel(query) {
            val sha256 = it[SHA256]?.toString()
            if (sha256 != null) {
                bf.put(sha256)
            }
        }

        val count = "${bf.approximateElementCount()}/${properties.expectedNodes}"
        val fpp = bf.expectedFpp()
        logger.info("Build bloom filter successful,count: $count,fpp: $fpp")
        return bf
    }

    /**
     * 是否在映射存储中存在相同sha256的引用
     */
    private fun existsRefOfMappingStorage(ref: FileReferenceData, collectionName: String): Boolean {
        // 查询是否存在映射的key
        val mappingKeys = storageKeyMapping[ref.credentialsKey ?: DEFAULT_STORAGE_KEY]
        if (mappingKeys.isNullOrEmpty()) {
            return false
        }

        mappingKeys.forEach { mappingKey ->
            // 兼容默认存储
            val mappingStorageKey = if (mappingKey == DEFAULT_STORAGE_KEY) {
                null
            } else {
                mappingKey
            }

            // 查询映射存储中是否存在对应的引用
            val criteria = Criteria.where(SHA256).isEqualTo(ref.sha256).and(CREDENTIALS).isEqualTo(mappingStorageKey)
            if (mongoTemplate.exists(Query(criteria), collectionName)) {
                return true
            }
        }

        return false
    }

    private fun getCredentials(key: String): StorageCredentials? {
        return cacheMap.getOrPut(key) {
            storageCredentialService.findByKey(key) ?: return null
        }
    }

    /**
     * 清理文件相关资源，如归档和压缩资源
     * */
    private fun cleanupRelatedResources(sha256: String, credentialsKey: String?): Boolean {
        val criteria = Criteria.where(SHA256).isEqualTo(sha256)
            .and(STORAGE_CREDENTIALS).isEqualTo(credentialsKey)
        val query = Query(criteria)
        var findAndDelete = false
        mongoTemplate.findOne(query, Node::class.java, COMPRESS_FILE_COLLECTION)?.let {
            val deleteCompressFileRequest = DeleteCompressRequest(sha256, credentialsKey, SYSTEM_USER)
            archiveClient.deleteCompress(deleteCompressFileRequest)
            findAndDelete = true
        }
        mongoTemplate.findOne(query, Node::class.java, ARCHIVE_FILE_COLLECTION)?.let {
            val deleteArchiveFileRequest = ArchiveFileRequest(sha256, credentialsKey, SYSTEM_USER)
            archiveClient.delete(deleteArchiveFileRequest)
            findAndDelete = true
        }
        return findAndDelete
    }

    /**
     * 是否是gc链中的base
     * */
    private fun isGcBase(sha256: String, credentialsKey: String?): Boolean {
        val criteria = Criteria.where(BASE_SHA256).isEqualTo(sha256)
            .and(STORAGE_CREDENTIALS).isEqualTo(credentialsKey)
            .and(STATUS).ne(CompressStatus.COMPRESS_FAILED)
        val query = Query(criteria)
        return mongoTemplate.findOne(query, Node::class.java, COMPRESS_FILE_COLLECTION) != null
    }

    private val cacheMap: ConcurrentHashMap<String, StorageCredentials?> = ConcurrentHashMap()

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME_PREFIX = "file_reference_"
        private const val COMPRESS_FILE_COLLECTION = "compress_file"
        private const val ARCHIVE_FILE_COLLECTION = "archive_file"
        private const val STORAGE_CREDENTIALS = "storageCredentialsKey"
        private const val BASE_SHA256 = "baseSha256"
        private const val STATUS = "status"
    }

    data class FileReferenceData(private val map: Map<String, Any?>) {
        val id: String? by map
        val sha256: String by map
        val credentialsKey: String? = map[CREDENTIALS] as String?
    }

    data class Node(
        val id: String,
        val sha256: String,
    )

    override fun mapToEntity(row: Map<String, Any?>): FileReferenceData {
        return FileReferenceData(row)
    }
}
