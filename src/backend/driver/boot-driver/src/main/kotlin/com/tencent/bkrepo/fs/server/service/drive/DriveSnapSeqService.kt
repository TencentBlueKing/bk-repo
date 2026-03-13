package com.tencent.bkrepo.fs.server.service.drive

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.fs.server.model.drive.TDriveSnapSeq
import com.tencent.bkrepo.fs.server.repository.drive.RDriveSnapSeqDao
import com.tencent.bkrepo.fs.server.utils.DriveServiceUtils
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import reactor.kotlin.core.publisher.toMono
import java.time.Duration
import java.time.LocalDateTime
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
            mono(refreshDispatcher) { queryLatestSnapSeq(key.projectId, key.repoName).snapSeq }.toFuture()
        }

    /**
     * 查询仓库当前快照序列号，不存在时抛出异常。
     */
    suspend fun getLatestSnapSeq(projectId: String, repoName: String, fromCache: Boolean = true): Long {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        return if (fromCache) {
            val cacheKey = SnapSeqCacheKey(projectId, repoName)
            snapSeqCache.get(cacheKey).toMono().awaitSingle()
        } else {
            queryLatestSnapSeq(projectId, repoName).snapSeq
        }
    }

    suspend fun createSnapSeq(projectId: String, repoName: String): TDriveSnapSeq {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        val now = LocalDateTime.now()
        val operator = DriveServiceUtils.getUserOrSystem()
        val snapSeq = TDriveSnapSeq(
            id = null,
            createdBy = operator,
            createdDate = now,
            lastModifiedBy = operator,
            lastModifiedDate = now,
            projectId = projectId,
            repoName = repoName,
            snapSeq = 0L,
        )
        return try {
            val driveSnapSeq = driveSnapSeqDao.insert(snapSeq)
            logger.info("Create drive snapSeq of repo [$projectId/$repoName] success.")
            driveSnapSeq
        } catch (_: DuplicateKeyException) {
            logger.info("Drive snapSeq of repo [$projectId/$repoName] already exists.")
            queryLatestSnapSeq(projectId, repoName)
        }
    }

    /**
     * 增加仓库快照序列号并返回增加后的值，两次增加的间隔不能少于[MIN_INC_INTERVAL_SECONDS],避免频繁创建快照或异常原因连续创建快照
     */
    suspend fun incSnapSeq(projectId: String, repoName: String, oldSeq: Long): Long {
        DriveServiceUtils.validateProjectRepo(projectId, repoName)
        val requiredInterval = LocalDateTime.now().minusSeconds(MIN_INC_INTERVAL_SECONDS)
        val now = LocalDateTime.now()
        val criteria = where(TDriveSnapSeq::projectId).isEqualTo(projectId)
            .and(TDriveSnapSeq::repoName.name).isEqualTo(repoName)
            .and(TDriveSnapSeq::snapSeq.name).isEqualTo(oldSeq)
            .and(TDriveSnapSeq::lastModifiedDate.name).lte(requiredInterval)
        val update = Update()
            .set(TDriveSnapSeq::lastModifiedDate.name, now)
            .set(TDriveSnapSeq::lastModifiedBy.name, DriveServiceUtils.getUserOrSystem())
            .inc(TDriveSnapSeq::snapSeq.name, 1L)
        val updateResult = driveSnapSeqDao.updateFirst(Query(criteria), update)
        if (updateResult.modifiedCount == 0L) {
            throw IllegalStateException("Failed to increase drive snap sequence for [$projectId/$repoName].")
        }
        logger.info("Increase drive snapSeq to [${oldSeq + 1}] in repo [$projectId/$repoName].")
        return oldSeq + 1
    }

    private suspend fun queryLatestSnapSeq(projectId: String, repoName: String): TDriveSnapSeq {
        val criteria = where(TDriveSnapSeq::projectId).isEqualTo(projectId)
            .and(TDriveSnapSeq::repoName.name).isEqualTo(repoName)
        return driveSnapSeqDao.findOne(Query(criteria))
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
        /**
         * 两次创建快照的最小间隔
         */
        private const val MIN_INC_INTERVAL_SECONDS = 60L
        private const val CACHE_REFRESH_SECONDS = 10L
        private const val CACHE_EXPIRE_SECONDS = 300L
        private const val MAXIMUM_CACHE_SIZE = 10_000L
        private val logger = LoggerFactory.getLogger(DriveSnapSeqService::class.java)
    }
}
