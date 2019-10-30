package com.tencent.bkrepo.common.storage.schedule

import com.tencent.bkrepo.common.storage.cache.FileCache
import java.text.DecimalFormat
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * 存储相关任务调度
 *
 * @author: carrypan
 * @date: 2019-10-21
 */
class StorageSchedule(private val fileCache: FileCache) {
    // 1小时执行一次
    // @Scheduled(fixedRate = 1000 * 60 * 60)
    @Scheduled(fixedRate = 1000 * 60 * 1)
    fun cleanLocalCacheFiles() {
        logger.info("开始清理缓存文件")
        try {
            val result = fileCache.onClean()

            val decimalFormat = DecimalFormat("0.000")
            val formattedSize = decimalFormat.format(result.size.toFloat() / 1024 / 1024)
            logger.info("清理缓存文件完成，共清理文件[${result.count}]个，大小[$formattedSize]MB")
        } catch (exception: Exception) {
            logger.error("清理缓存文件失败: ${exception.message}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageSchedule::class.java)
    }
}
