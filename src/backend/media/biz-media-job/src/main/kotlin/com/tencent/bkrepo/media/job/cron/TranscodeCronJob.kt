package com.tencent.bkrepo.media.job.cron

import com.tencent.bkrepo.media.common.dao.MediaTranscodeJobDao
import com.tencent.bkrepo.media.common.dao.TranscodeJobConfigDao
import com.tencent.bkrepo.media.job.service.TranscodeJobService
import com.tencent.bkrepo.media.job.config.MediaJobProperties
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
     * 遍历所有配置，有projectId的走独立配额，其余走默认配置
     */
    @Scheduled(fixedDelay = 1 * 1000)
    fun startTranscodeJob() {
        var wasExecuted = false
        lockProvider.lock(getLockConfiguration()).ifPresent {
            lock = it
            it.use {
                try {
                    doStartTranscodeJob()
                } catch (e: Exception) {
                    logger.error("Job mediaTranscodeCronJob start error for default config", e)
                }
            }
            lock = null
            wasExecuted = true
        }
        if (!wasExecuted) {
            logger.warn("startTranscodeJob already execution")
        }
    }

    fun doStartTranscodeJob() {
        val configs = transcodeJobConfigDao.findAll()
        if (configs.isEmpty()) {
            logger.error("startTranscodeJob no config found")
            return
        }

        // 分离特殊项目配置和默认配置
        val projectConfigs = configs.filter { !it.projectId.isNullOrBlank() }
        val defaultConfig = configs.find { it.projectId.isNullOrBlank() }

        // 特殊项目的projectId集合，用于默认配置排除
        val specialProjectIds = projectConfigs.mapNotNull { it.projectId }.toSet()

        // 一次聚合查询获取所有项目的运行中任务数
        val projectJobCountMap = mediaTranscodeJobDao.queueAndRunningJobCountGroupByProject()

        // 处理每个特殊项目配置
        for (config in projectConfigs) {
            val projectId = config.projectId ?: continue
            val maxJobCount = config.maxJobCount ?: 66
            val currentJobCount = projectJobCountMap[projectId] ?: 0L
            if (currentJobCount >= maxJobCount) {
                logger.info(
                    "Project $projectId exceeded max job limit: current=$currentJobCount, max=$maxJobCount"
                )
                continue
            }
            logger.info(
                "starting transcode job for project $projectId, maxJob:$maxJobCount, " +
                        "currentJobCount:$currentJobCount"
            )
            try {
                transcodeJobService.startJobForProject(config, projectId)
            } catch (e: Exception) {
                logger.error("Job mediaTranscodeCronJob start error for project $projectId", e)
            }
        }

        // 处理默认配置（未配置独立项目的任务）
        if (defaultConfig != null) {
            val maxJobCount = defaultConfig.maxJobCount ?: 66
            // 排除特殊项目后统计默认配额的运行中任务数
            val currentJobCount = projectJobCountMap.entries
                .filter { it.key !in specialProjectIds }
                .sumOf { it.value }
            if (currentJobCount >= maxJobCount) {
                logger.debug(
                    "Default config exceeded max job limit: current=$currentJobCount, max=$maxJobCount"
                )
                return
            }
            logger.debug(
                "starting transcode job for default config, maxJob:$maxJobCount, " +
                        "currentJobCount:$currentJobCount"
            )
            try {
                transcodeJobService.startJobForDefault(defaultConfig, specialProjectIds)
            } catch (e: Exception) {
                logger.error("Job mediaTranscodeCronJob start error for default config", e)
            }
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