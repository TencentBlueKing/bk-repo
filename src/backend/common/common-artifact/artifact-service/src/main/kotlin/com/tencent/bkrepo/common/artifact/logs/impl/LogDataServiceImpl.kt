package com.tencent.bkrepo.common.artifact.logs.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.logs.LogData
import com.tencent.bkrepo.common.artifact.logs.LogDataService
import com.tencent.bkrepo.common.artifact.logs.LogType
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDateTime
import java.util.regex.Pattern
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service


@Service
class LogDataServiceImpl : LogDataService {


    @Value("\${logging.path:}")
    private val logPath = StringPool.EMPTY


    @Value("\${spring.application.name}")
    var applicationName: String = StringPool.EMPTY

    override fun getLogData(logType: LogType, startPosition: Long, maxSize: Long): LogData {
        val suffix = when (logType) {
            LogType.APP -> ".log"
            LogType.JOB -> "-job.log"
            LogType.ERROR -> "-error.log"
            LogType.ACCESS -> "-access.log"
        }
        val log = logPath.trimEnd('/') + StringPool.SLASH + applicationName + suffix
        return readLogsFromPosition(log, startPosition, maxSize)
    }


    fun readLogsFromPosition(filePath: String, startPosition: Long, maxSize: Long): LogData {
        val logFile = File(filePath)
        if (!logFile.exists()) {
            return LogData(0, StringPool.EMPTY, LocalDateTime.now().toString())
        }

        val fileSize = logFile.length()
        var endPosition = minOf(fileSize, startPosition + maxSize) // 计算读取的结束位置

        RandomAccessFile(logFile, "r").use { raf ->
            raf.seek(startPosition)

            // 读取内容
            val buffer = ByteArray((endPosition - startPosition).toInt())
            raf.read(buffer)
            var content = String(buffer)

            // 确保每行日志完整
            if (endPosition < fileSize) {
                // 查找最后一个换行符
                val lastNewline = content.lastIndexOf('\n')
                if (lastNewline != -1) {
                    content = content.substring(0, lastNewline + 1)
                    endPosition = startPosition + lastNewline + 1
                } else {
                    content = "" // 如果没有换行符，说明内容不完整，丢弃
                }
            }
            val lastUpdateLabel = parseTimestampFromLastLine(content) ?: LocalDateTime.now().toString()
            return LogData(endPosition, content, lastUpdateLabel)
        }
    }

    fun parseTimestampFromLastLine(content: String): String? {
        val lines = content.trim().lines()
        if (lines.isEmpty()) return null

        val lastLine = lines.last()
        val pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}")
        val matcher = pattern.matcher(lastLine)
        return if (matcher.find()) matcher.group() else null
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(LogDataServiceImpl::class.java)
    }
}