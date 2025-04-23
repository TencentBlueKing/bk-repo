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
        var lineEnd = bytes.size - 1
        while (lineEnd >= 0 && bytes[lineEnd] != '\n'.code.toByte()) {
            lineEnd--
        }
        val lineStart = if (lineEnd >= 0) lineEnd + 1 else 0
        // 直接处理字节数组
        val lastLine = String(bytes, lineStart, bytes.size - lineStart).trim()
        // 如果最后一行非空，尝试提取时间戳
        if (lastLine.isNotEmpty()) {
            return extractTimestamp(lastLine) ?: run {
                // 如果最后一行没有时间戳，尝试倒数第二行
                findPreviousLineTimestamp(bytes, lineEnd)
            }
        }
        // 如果最后一行是空的，直接找倒数第二行
        return findPreviousLineTimestamp(bytes, lineEnd)
    }

    /**
     * 从字节数组中查找上一行的时间戳
     */
    private fun findPreviousLineTimestamp(bytes: ByteArray, currentLineEnd: Int): String? {
        if (currentLineEnd <= 0) return null

        // 查找上一行的起始位置
        var prevLineEnd = currentLineEnd - 1
        while (prevLineEnd >= 0 && bytes[prevLineEnd] != '\n'.code.toByte()) {
            prevLineEnd--
        }

        val prevLineStart = if (prevLineEnd >= 0) prevLineEnd + 1 else 0
        val prevLine = String(bytes, prevLineStart, currentLineEnd - prevLineStart).trim()

        return extractTimestamp(prevLine)
    }

    /**
     * 从单行文本中提取时间戳
     */
    private fun extractTimestamp(line: String): String? {
        val timestampRegex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""")
        return timestampRegex.find(line)?.value
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(LogDataServiceImpl::class.java)
    }
}