package com.tencent.bkrepo.common.service.log.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.service.log.LogData
import com.tencent.bkrepo.common.service.log.LogDataService
import com.tencent.bkrepo.common.service.log.LogType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDateTime


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
        return try {
            readLogsFromPosition(log, startPosition, maxSize)
        } catch (e: Exception) {
            logger.warn("read log data error: $e")
            LogData()
        }
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
            // 直接处理字节数组，避免创建完整字符串
            val lastNewline = buffer.indexOfLast { it == '\n'.code.toByte() }
            val (contentBytes, newEndPos) = if (endPosition < fileSize && lastNewline != -1) {
                buffer.copyOfRange(0, lastNewline + 1) to (startPosition + lastNewline + 1)
            } else if (endPosition < fileSize) {
                byteArrayOf() to startPosition // 内容不完整时返回空
            } else {
                buffer to endPosition
            }
            val content = String(contentBytes)
            val lastUpdateLabel = parseTimestampFromLastLineOptimized(contentBytes) ?: LocalDateTime.now().toString()
            return LogData(endPosition, content, lastUpdateLabel)
        }
    }

    private fun parseTimestampFromLastLineOptimized(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        // 从后向前查找最后一个换行符
        var lineStart = bytes.size - 1
        while (lineStart >= 0 && bytes[lineStart] != '\n'.code.toByte()) {
            lineStart--
        }
        lineStart++ // 跳过换行符
        // 直接处理字节数组
        val pattern = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}".toRegex()
        val lastLine = String(bytes, lineStart, bytes.size - lineStart)
        return pattern.find(lastLine)?.value
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(LogDataServiceImpl::class.java)
    }
}