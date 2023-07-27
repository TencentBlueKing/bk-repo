package com.tencent.bkrepo.repository.job.clean

import com.tencent.bkrepo.repository.service.repo.RepositoryCleanService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor

/**
 * 线程池执行任务
 */
@Suppress("TooGenericExceptionCaught")
@Component
class CleanRepoJobExecutor(
    private val repositoryCleanService: RepositoryCleanService
) {
    private val threadPoolExecutor: ThreadPoolExecutor = CleanThreadPoolExecutor.instance

    /**
     * 执行同步任务
     * @param taskId 仓库表id
     * 该任务只能由一个节点执行，已经成功抢占到锁才能执行到此处
     */
    fun execute(taskId: String) {
        logger.info("Start to execute clean repo task[$taskId].")
        try {
            threadPoolExecutor.submit {
                repositoryCleanService.cleanRepo(taskId)
            }
        } catch (exception: Exception) {
            logger.error("clean repository job [taskId:$taskId] exception ,exception:[$exception]")
        }


    }

    companion object {
        private val logger = LoggerFactory.getLogger(CleanRepoJobExecutor::class.java)
    }
}
