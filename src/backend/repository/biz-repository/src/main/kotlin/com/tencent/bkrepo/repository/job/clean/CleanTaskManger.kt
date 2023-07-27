package com.tencent.bkrepo.repository.job.clean

import com.tencent.bkrepo.common.artifact.pojo.configuration.clean.CleanStatus
import com.tencent.bkrepo.repository.config.RepositoryProperties
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.Trigger
import org.quartz.TriggerBuilder

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * 清理任务管理类
 */
@Service
class CleanTaskManger(
    private val repositoryService: RepositoryService,
    private val taskScheduler: CleanRepoTaskScheduler
) {
    @Autowired
    lateinit var repositoryProperties: RepositoryProperties

    /**
     * 查询所有仓库（本地仓库 & 组合仓库）
     * 遍历仓库，获取仓库的 cleanStrategy
     * 判断【autoClean】
     *      true：判断job是否存在，不存在，创建；存在，不处理
     *      false：判断job是否存在，存在，删除；不存在，不处理
     */
    @Scheduled(initialDelay = RELOAD_INITIAL_DELAY, fixedDelay = RELOAD_FIXED_DELAY)
    fun reloadCleanTask() {
        var skip = 0L
        var repoList = repositoryService.allRepoPage(skip)
        while (repoList.isNotEmpty()) {
            skip += repoList.size
            repoList.forEach { repo ->
                createCleanJob(repo)
            }
            repoList = repositoryService.allRepoPage(skip)
        }
        if (logger.isDebugEnabled) {
            logger.debug("Success to reload clean task, now exist job list:[${taskScheduler.listJobKeys()}]")
        }
    }

    fun createCleanJob(repo: TRepository) {
        val repoId = repo.id!!
        val cleanStrategy = repositoryService.getRepoCleanStrategy(repo.projectId, repo.name)
        cleanStrategy?.let {
            //开启，不存在，创建
            if (it.autoClean && !taskScheduler.exist(repoId)) {
                val jobDetail = createJobDetail(repoId)
                val trigger = createTrigger(repoId, repositoryProperties.cleanStrategyTime)
                taskScheduler.scheduleJob(jobDetail, trigger)
            }
            // 关闭，存在，任务状态为 WAITING，则删除；
            // 任务状态为 RUNNING，这里不做处理
            if (!it.autoClean && taskScheduler.exist(repoId) && it.status == CleanStatus.WAITING) {
                taskScheduler.deleteJob(repoId)
            }
        }
    }

    /**
     * 根据任务信息创建job detail
     */
    private fun createJobDetail(id: String): JobDetail {
        return JobBuilder.newJob(CleanRepoJob::class.java)
            .withIdentity(id, CLEAN_JOB_GROUP)
            .requestRecovery()
            .build()
    }

    /**
     * 根据任务信息创建job trigger
     */
    private fun createTrigger(id: String, cronExpression: String): Trigger {
        return TriggerBuilder.newTrigger().withIdentity(id, CLEAN_JOB_GROUP)
            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
            .build()
    }

    companion object {

        private val logger = LoggerFactory.getLogger(CleanTaskManger::class.java)

        /**
         * quartz scheduler的job group名称
         */
        const val CLEAN_JOB_GROUP = "CLEAN"

        /**
         * 进程启动后加载任务延迟时间
         */
        private const val RELOAD_INITIAL_DELAY = 10 * 1000L

        /**
         * 重新加载任务固定延迟时间 30分钟
         */
        private const val RELOAD_FIXED_DELAY = 1800 * 1000L
    }
}
