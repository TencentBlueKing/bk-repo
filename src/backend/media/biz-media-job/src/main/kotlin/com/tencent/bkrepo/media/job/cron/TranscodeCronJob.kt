package com.tencent.bkrepo.media.job.cron

import com.tencent.bkrepo.media.common.dao.MediaTranscodeJobDao
import com.tencent.bkrepo.media.common.dao.TranscodeJobConfigDao
import com.tencent.bkrepo.media.job.service.TranscodeJobService
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJobConfig
import com.tencent.bkrepo.media.job.config.MediaJobProperties
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class TranscodeCronJob @Autowired constructor(
    private val mediaJobProperties: MediaJobProperties,
    private val mediaTranscodeJobDao: MediaTranscodeJobDao,
    private val transcodeJobConfigDao: TranscodeJobConfigDao,
    private val transcodeJobService: TranscodeJobService,
) {

    @Autowired
    private lateinit var lockProvider: LockProvider

    private var lock: SimpleLock? = null

    /**
     * 定时下发转码任务
     */
    @Scheduled(fixedDelay = 1 * 1000)
    fun startTranscodeJob() {
        // 判断是否超出最大任务数上线
        val config =
            transcodeJobConfigDao.findOne(Query(where(TMediaTranscodeJobConfig::projectId).`is`(null))) ?: kotlin.run {
                logger.error("startTranscodeJob no default config")
                return
            }
        val maxJobCount = config.maxJobCount ?: 66
        val currentJobCount = mediaTranscodeJobDao.queueAndRunningJobCount()
        if (currentJobCount >= maxJobCount) {
            return
        }

        logger.debug("starting transcode job maxJob:$maxJobCount, currentJobCount:$currentJobCount")

        var wasExecuted = false
        lockProvider.lock(getLockConfiguration()).ifPresent {
            lock = it
            it.use { doStart(config) }
            lock = null
            wasExecuted = true
        }
        if (!wasExecuted) {
            logger.warn("Job mediaTranscodeCronJob already execution")
        }
    }

    private fun doStart(config: TMediaTranscodeJobConfig) {
        try {
            transcodeJobService.startJob(config)
        } catch (e: Exception) {
            logger.error("Job mediaTranscodeCronJob start error", e)
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")
    fun performCleanup() {
        logger.info("performCleanup scheduled cleanup of old successful jobs...")
        val result = mediaTranscodeJobDao.deleteOldSuccessfulJobs(mediaJobProperties.cleanSuccessJobDays)
        logger.info("performCleanup cleanup. Deleted ${result.deletedCount} jobs.")
    }

    /**
     * 定时重试失败的转码任务，每两小时执行一次
     */
    @Scheduled(cron = "0 0 */2 * * ?")
    fun retryFailedJobs() {
        var wasExecuted = false
        lockProvider.lock(getRetryLockConfiguration()).ifPresent {
            lock = it
            it.use { doRetryFailedJobs() }
            lock = null
            wasExecuted = true
        }
        if (!wasExecuted) {
            logger.warn("Job retryFailedJobs already execution")
        }
    }

    private fun doRetryFailedJobs() {
        try {
            val failedJobs = mediaTranscodeJobDao.findFailedJobs(
                limit = mediaJobProperties.retryFailedJobBatchSize,
            )
            if (failedJobs.isEmpty()) {
                logger.info("retryFailedJobs no failed jobs to retry")
                return
            }
            val jobIds = failedJobs.mapNotNull { it.id }.toSet()
            if (jobIds.isNotEmpty()) {
                logger.info("retryFailedJobs retrying ${jobIds.size} failed jobs: $jobIds")
                transcodeJobService.restartJob(jobIds)
            }
        } catch (e: Exception) {
            logger.error("Job retryFailedJobs error", e)
        }
    }

    /**
     * 获取分布式锁需要的锁配置
     * */
    private fun getLockConfiguration(): LockConfiguration {
        return LockConfiguration(
            /* name = */ "mediaTranscodeCronJob",
            /* lockAtMostFor = */ Duration.ofMillis(999),
            /* lockAtLeastFor = */ Duration.ofMillis(500)
        )
    }

    /**
     * 获取重试失败任务分布式锁需要的锁配置
     * */
    private fun getRetryLockConfiguration(): LockConfiguration {
        return LockConfiguration(
            /* name = */ "retryFailedTranscodeJob",
            /* lockAtMostFor = */ Duration.ofSeconds(60),
            /* lockAtLeastFor = */ Duration.ofSeconds(5)
        )
    }

    /**
     * 使用锁,[block]运行完后，将会释放锁
     * */
    private fun SimpleLock.use(block: () -> Unit) {
        try {
            block()
        } finally {
            doUnlock()
        }
    }

    /**
     * 静默释放锁
     * */
    private fun SimpleLock.doUnlock() {
        try {
            unlock()
        } catch (e: Exception) {
            logger.error("Unlock failed", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranscodeCronJob::class.java)
    }
}