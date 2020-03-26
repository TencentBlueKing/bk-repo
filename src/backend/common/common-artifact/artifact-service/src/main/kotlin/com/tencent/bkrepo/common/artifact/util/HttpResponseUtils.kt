package com.tencent.bkrepo.common.artifact.util

import com.google.common.io.ByteStreams
import com.tencent.bkrepo.common.api.constant.StringPool.MEDIA_TYPE_STREAM
import com.tencent.bkrepo.common.artifact.config.BYTES
import com.tencent.bkrepo.common.artifact.config.CONTENT_DISPOSITION_TEMPLATE
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.web.server.MimeMappings
import org.springframework.http.HttpHeaders
import org.springframework.web.util.UriUtils
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import javax.servlet.http.HttpServletResponse

/**
 * Http响应工具类
 * @author: carrypan
 * @date: 2019/11/28
 */
object HttpResponseUtils {

    private val logger = LoggerFactory.getLogger(HttpResponseUtils::class.java)

    fun response(filename: String, file: File) {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        val fileLength = file.length()
        var readStart = 0L
        var readEnd = fileLength

        val disposition = encodeDisposition(filename)
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition)
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes")

        request.getHeader(HttpHeaders.RANGE)?.run {
            parseContentRange(this, fileLength)?.run {
                first?.let { readStart = first!! }
                second?.let { readEnd = second!! + 1 }

                response.status = HttpServletResponse.SC_PARTIAL_CONTENT
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes $first-$second/$fileLength")
            } ?: run {
                response.status = HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE
                logger.warn("Range download failed: range is invalid")
                return
            }
        }
        response.setHeader(HttpHeaders.CONTENT_LENGTH, (readEnd - readStart).toString())
        response.contentType = determineMediaType(filename)
        response.characterEncoding = "UTF-8"

        RandomAccessFile(file, "r").use {
            it.seek(readStart)
            val out = BufferedOutputStream(response.outputStream)

            val bufferSize = 1024
            val buffer = ByteArray(bufferSize)

            var current = readStart
            while ((current + bufferSize) <= readEnd) {
                val readLength = it.read(buffer)
                current += readLength
                out.write(buffer, 0, readLength)
            }
            if (current < readEnd) {
                val readLength = it.read(buffer, 0, (readEnd - current).toInt())
                out.write(buffer, 0, readLength)
            }
            out.flush()
        }
    }

    fun response(filename: String, inputStream: InputStream) {
        val response = HttpContextHolder.getResponse()
        val fileLength = inputStream.available()
        val disposition = encodeDisposition(filename)
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition)
        response.setHeader(HttpHeaders.CONTENT_LENGTH, fileLength.toString())
        response.contentType = determineMediaType(filename)
        response.characterEncoding = "UTF-8"
        inputStream.use {
            val out = BufferedOutputStream(response.outputStream)
            ByteStreams.copy(inputStream, out)
            out.flush()
        }
    }

    private fun parseContentRange(rangeHeader: String?, total: Long): Pair<Long?, Long?>? {
        return try {
            rangeHeader?.takeIf { it.isNotBlank() && it.contains("-") && it.contains(BYTES) }?.run {
                val items = rangeHeader.replace(BYTES, "").trim().split("-")
                if (items.size < 2) return null

                val left = if (items[0].isBlank()) null else items[0].toLong()
                val right = if (items[1].isBlank()) null else items[1].toLong()
                // check parameter
                if (left == null && right == null) return null
                if (left?.compareTo(right ?: total - 1) ?: -1 >= 0) return null
                if (right?.compareTo(total - 1) ?: -1 >= 0) return null
                if (right?.compareTo(0) ?: 1 <= 0) return null

                return when {
                    left == null -> Pair(total - right!!, total - 1)
                    right == null -> Pair(left, total - 1)
                    else -> Pair(left, right)
                }
            }
        } catch (exception: Exception) {
            null
        }
    }

    private fun determineMediaType(name: String): String {
        return MimeMappings.DEFAULT.get(NodeUtils.getExtension(name)) ?: MEDIA_TYPE_STREAM
    }

    private fun encodeDisposition(filename: String): String {
        val encodeFilename = UriUtils.encode(filename, Charsets.UTF_8)
        return CONTENT_DISPOSITION_TEMPLATE.format(encodeFilename, encodeFilename)
    }
}
