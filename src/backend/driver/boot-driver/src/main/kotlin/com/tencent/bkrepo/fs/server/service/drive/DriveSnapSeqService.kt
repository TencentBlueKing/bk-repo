package com.tencent.bkrepo.fs.server.service.drive

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapSeq
import com.tencent.bkrepo.fs.server.repository.drive.RDriveSnapSeqDao
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.util.concurrent.Executors

/**
 * Drive 快照序列号服务
 */
@Service
@Conditional(ReactiveCondition::class)
class DriveSnapSeqService(
    private val driveSnapSeqDao: RDriveSnapSeqDao,
) {
    private val refreshExecutor = Executors.newFixedThreadPool(
        1,
        ThreadFactoryBuilder().setNameFormat("drive-snap-seq-refresh-%d").build(),
    ).trace()
    private val refreshDispatcher = refreshExecutor.asCoroutineDispatcher()

    private val snapSeqCache: AsyncLoadingCache<SnapSeqCacheKey, Long> = Caffeine.newBuilder()
        .executor(refreshExecutor)
        // 10秒后触发异步刷新，读取可先返回旧值；5分钟后强制过期，避免异常场景下长期持有陈旧数据。
        .refreshAfterWrite(Duration.ofSeconds(CACHE_REFRESH_SECONDS))
        .expireAfterWrite(Duration.ofSeconds(CACHE_EXPIRE_SECONDS))
        .maximumSize(MAXIMUM_CACHE_SIZE)
        .buildAsync { key, _ ->
            mono(refreshDispatcher) { queryLatestSnapSeq(key.projectId, key.repoName) }.toFuture()
        }

    /**
     * 查询仓库当前快照序列号，不存在时抛出异常。
     */
    suspend fun getLatestSnapSeq(projectId: String, repoName: String): Long {
        validate(projectId, repoName)
        val cacheKey = SnapSeqCacheKey(projectId, repoName)
        return snapSeqCache.get(cacheKey).toMono().awaitSingle()
    }

    /**
     * 增加仓库快照序列号并返回增加后的值。
     */
    suspend fun incSnapSeq(projectId: String, repoName: String): Long {
        validate(projectId, repoName)
        val criteria = where(TDriveSnapSeq::projectId).isEqualTo(projectId)
            .and(TDriveSnapSeq::repoName.name).isEqualTo(repoName)
        val query = Query(criteria)
        val update = Update()
            .setOnInsert(TDriveSnapSeq::projectId.name, projectId)
            .setOnInsert(TDriveSnapSeq::repoName.name, repoName)
            .inc(TDriveSnapSeq::snapSeq.name, 1L)
        val options = FindAndModifyOptions.options().upsert(true).returnNew(true)
        val latest = driveSnapSeqDao.findAndModify(query, update, options, TDriveSnapSeq::class.java)
            ?: throw IllegalStateException("Failed to increase drive snap sequence for [$projectId/$repoName].")
        logger.info("Increase drive snapSeq to [${latest.snapSeq}] in repo [$projectId/$repoName].")
        return latest.snapSeq
    }

    private fun validate(projectId: String, repoName: String) {
        Preconditions.checkArgument(projectId.isNotBlank(), TDriveSnapSeq::projectId.name)
        Preconditions.checkArgument(repoName.isNotBlank(), TDriveSnapSeq::repoName.name)
    }

    private suspend fun queryLatestSnapSeq(projectId: String, repoName: String): Long {
        val criteria = where(TDriveSnapSeq::projectId).isEqualTo(projectId)
            .and(TDriveSnapSeq::repoName.name).isEqualTo(repoName)
        return driveSnapSeqDao.findOne(Query(criteria))?.snapSeq
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "drive snapSeq[$projectId/$repoName]")
    }

    @PreDestroy
    fun shutdownRefreshExecutor() {
        refreshExecutor.shutdown()
    }

    private data class SnapSeqCacheKey(
        val projectId: String,
        val repoName: String,
    )

    companion object {
        private const val CACHE_REFRESH_SECONDS = 10L
        private const val CACHE_EXPIRE_SECONDS = 300L
        private const val MAXIMUM_CACHE_SIZE = 10_000L
        private val logger = LoggerFactory.getLogger(DriveSnapSeqService::class.java)
    }
}
