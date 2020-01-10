package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 清理缓存文件定时任务
 * @author: carrypan
 * @date: 2019/12/24
 */
@Component
class ExpiredCacheFileCleanupJob {

    @Autowired
    private lateinit var storageService: StorageService

    @Scheduled(cron = "0 0 4 * * ?") // 每天凌晨4点执行
    @SchedulerLock(name = "ExpiredCacheFileCleanupJob", lockAtMostFor = "PT10H")
    fun cleanUp() {
        logger.info("Starting to clean up expired cache files.")
        val startTimeMillis = System.currentTimeMillis()
        val result = storageService.cleanUp()
        val elapseTimeMillis = System.currentTimeMillis() - startTimeMillis
        logger.info("Clean up [${result.count}] expired cache and temp files, [${result.size}] bytes totally, elapse [$elapseTimeMillis] ms.")
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
