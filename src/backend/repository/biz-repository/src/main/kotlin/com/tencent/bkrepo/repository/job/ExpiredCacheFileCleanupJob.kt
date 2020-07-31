package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.api.util.executeAndMeasureTime
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
        executeAndMeasureTime {
            storageService.cleanUp()
        }.apply {
            logger.info(
                "[${first.getTotal()}] expired cache and temp files has been clean up" +
                    ", file[${first.fileCount}], folder[${first.folderCount}]" +
                    ", [${first.size}] bytes totally, elapse [${second.seconds}] s."
            )
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
