package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.api.util.executeAndMeasureTime
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 文件同步任务
 */
@Component
class FileSynchronizeJob {

    @Autowired
    private lateinit var storageService: StorageService

    @Scheduled(cron = "0 0 0 ? * 6")
    @Async
    @SchedulerLock(name = "FileSynchronizeJob", lockAtMostFor = "P7D")
    fun run() {
        logger.info("Starting to synchronize file.")
        executeAndMeasureTime {
            storageService.synchronizeFile()
        }.apply {
            logger.info(
                "Synchronize file success. Walked [${first.totalCount}] files totally, synchronized[${first.synchronizedCount}]," +
                    " error[${first.errorCount}], ignored[${first.ignoredCount}]" +
                    ", [${first.totalSize}] bytes totally, elapse [${second.seconds}] s."
            )
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
