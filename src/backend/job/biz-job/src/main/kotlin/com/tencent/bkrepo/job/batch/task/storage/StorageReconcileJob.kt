package com.tencent.bkrepo.job.batch.task.storage

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.CREDENTIALS
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.config.properties.FileReferenceCleanupJobProperties
import com.tencent.bkrepo.job.config.properties.StorageReconcileJobProperties
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * 实际存储与文件引用进行对账，删除存储中多余的文件。
 * 支持安全模式，在安全模式下，不区分数据库中的存储实例，
 * 只要数据库存在文件引用，就不会删除实际存储。
 * */
@Component
@EnableConfigurationProperties(StorageReconcileJobProperties::class)
@Suppress("UnstableApiUsage")
class StorageReconcileJob(
    private val properties: StorageReconcileJobProperties,
    private val bloomFilterProp: FileReferenceCleanupJobProperties,
    private val fileStorage: FileStorage,
    private val clusterProperties: ClusterProperties,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val storageProperties: StorageProperties,
    private val fileReferenceService: FileReferenceService,
) : DefaultContextJob(properties) {
    override fun doStart0(jobContext: JobContext) {
        // 校验默认存储
        reconcile(storageProperties.defaultStorageCredentials())

        // 校验其他存储
        storageCredentialsClient.list(clusterProperties.region).data?.forEach {
            reconcile(it)
        }
    }

    /**
     * 存储对账，只会处理sha256为文件名的文件
     * 通过二次确认，以保证不会存在误删
     * 1. 先加载引用快照
     * 2. 比对实际存储中的文件，筛选出待删除文件列表
     * 3. 再次加载引用快照，从待删除文件列表中确定需要删除的文件。
     * */
    private fun reconcile(storageCredentials: StorageCredentials) {
        val credentialsKey = storageCredentials.key
        logger.info("Start reconcile storage [$credentialsKey]")
        val firstRefSnapshot = buildBloomFilter(storageCredentials)
        var total = 0L
        var deleted = 0L
        val pendingDeleteList = mutableSetOf<String>()
        fileStorage.listAll(StringPool.ROOT, storageCredentials).map { it.toFile().name }.forEach {
            total++
            if (it.length == SHA256_LEN && !firstRefSnapshot.mightContain(it)) {
                // 准备删除
                logger.info("File [$it] miss ref.")
                pendingDeleteList.add(it)
            }
        }
        // 二次确认,因为待确认的文件数量远低于总体数量，所以这里采用直接查表，效率更高些。
        pendingDeleteList.forEach {
            val exists = fileReferenceService.exists(it, credentialsKey)
            if (!exists) {
                logger.info("Delete file [$it]")
                fileReferenceService.increment(it, credentialsKey, 0)
                deleted++
            }
        }
        logger.info("Reconcile storage [$credentialsKey] successful, deleted[$deleted], total[$total].")
    }

    private fun buildBloomFilter(storageCredentials: StorageCredentials): BloomFilter<CharSequence> {
        logger.info("Start build bloom filter.")
        val bf = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            bloomFilterProp.expectedNodes,
            bloomFilterProp.fpp,
        )
        val criteria = Criteria().apply {
            if (!properties.safeMode) {
                where(CREDENTIALS).isEqualTo(storageCredentials.key)
            } else {
                logger.info("Work in safe mode")
            }
        }
        val query = Query(criteria)
        query.fields().include(SHA256)
        NodeCommonUtils.forEachRefByCollectionParallel(query) {
            val sha256 = it[SHA256]?.toString()
            if (sha256 != null) {
                bf.put(sha256)
            }
        }
        val count = "${bf.approximateElementCount()}/${bloomFilterProp.expectedNodes}"
        val fpp = bf.expectedFpp()
        logger.info("Build bloom filter successful,key: ${storageCredentials.key},count: $count,fpp: $fpp")
        return bf
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageReconcileJob::class.java)
        private const val SHA256_LEN = 64
    }
}
