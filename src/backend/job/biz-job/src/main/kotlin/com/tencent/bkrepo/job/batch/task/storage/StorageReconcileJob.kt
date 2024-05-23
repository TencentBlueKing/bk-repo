package com.tencent.bkrepo.job.batch.task.storage

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.tencent.bkrepo.common.api.constant.StringPool
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
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * 设计目标
 * 1. 找出未引用的cos
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
    private val fileReferenceClient: FileReferenceClient,
) : DefaultContextJob(properties) {
    override fun doStart0(jobContext: JobContext) {
        // 校验默认存储
        reconcile(storageProperties.defaultStorageCredentials())

        // 校验其他存储
        storageCredentialsClient.list(clusterProperties.region).data?.forEach {
            reconcile(it)
        }
    }

    private fun reconcile(storageCredentials: StorageCredentials) {
        logger.info("Start reconcile storage [${storageCredentials.key}]")
        val bf = buildBloomFilter(storageCredentials)
        var total = 0L
        var deleted = 0L
        fileStorage.listAll(StringPool.ROOT, storageCredentials).map { it.toFile().name }.forEach {
            total++
            if (!bf.mightContain(it)) {
                logger.info("File [$it] miss ref.")
                fileReferenceClient.increment(it, storageCredentials.key, 0)
                deleted++
            }
        }
        logger.info("Reconcile storage [${storageCredentials.key}] successful, deleted[$deleted], total[$total].")
    }

    private fun buildBloomFilter(storageCredentials: StorageCredentials): BloomFilter<CharSequence> {
        logger.info("Start build bloom filter.")
        val bf = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            bloomFilterProp.expectedNodes,
            bloomFilterProp.fpp,
        )
        val query = Query(Criteria.where(CREDENTIALS).isEqualTo(storageCredentials.key))
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
    }
}
