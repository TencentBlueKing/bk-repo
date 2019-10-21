package com.tencent.bkrepo.common.storage.schedule

import com.tencent.bkrepo.common.storage.core.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.text.DecimalFormat



/**
 * 存储相关任务调度
 *
 * @author: carrypan
 * @date: 2019-10-21
 */
@Component
class StorageSchedule @Autowired constructor(
        private val fileStorage: FileStorage
){

    // 10分钟执行一次
    @Scheduled(fixedRate = 1000 * 60 * 10)
    fun cleanLocalCacheFiles() {
        val properties = fileStorage.getStorageProperties()
        if(properties.localCache.enabled) {
            val directory = File(properties.localCache.path)
            val files = directory.listFiles() ?: arrayOf()
            var count = 0
            var size = 0L
            files.forEach {
                if(it.isFile && isExpired(it, properties.localCache.expires)) {
                    val fileSize = it.length()
                    if(it.delete()) {
                        count += 1
                        size += fileSize
                    }
                }
            }
            val df = DecimalFormat("0.000")
            val formattedSize = df.format(size.toFloat()/1024/1024)
            logger.info("清理本地缓存完成，共清理文件[$count]个，大小[$formattedSize]MB")
        }
    }

    private fun isExpired(file: File, expireSeconds: Long): Boolean {
        val isFile = file.isFile
        val isExpired = (System.currentTimeMillis() - file.lastModified()) >= expireSeconds * 1000
        return isFile && isExpired
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageSchedule::class.java)
    }
}