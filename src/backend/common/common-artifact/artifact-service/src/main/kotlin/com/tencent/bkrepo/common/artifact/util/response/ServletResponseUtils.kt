package com.tencent.bkrepo.common.artifact.util.response

import com.tencent.bkrepo.common.api.constant.StringPool.DASH
import com.tencent.bkrepo.common.artifact.config.BYTES
import com.tencent.bkrepo.common.artifact.config.CONTENT_DISPOSITION_TEMPLATE
import com.tencent.bkrepo.common.artifact.config.ICO_MIME_TYPE
import com.tencent.bkrepo.common.artifact.config.STREAM_MIME_TYPE
import com.tencent.bkrepo.common.artifact.config.TGZ_MIME_TYPE
import com.tencent.bkrepo.common.artifact.config.YAML_MIME_TYPE
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.util.NodeUtils
import org.springframework.boot.web.server.MimeMappings
import org.springframework.http.HttpHeaders
import org.springframework.web.util.UriUtils
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Servlet 文件响应工具类
 * @author: carrypan
 * @date: 2019/11/28
 */
object ServletResponseUtils {

    private val rangePatternRegex = Regex("^bytes=\\d*-\\d*(,\\d*-\\d*)*$")

    private const val BYTERANGES_BOUNDARY = "BYTERANGES_BOUNDRY"
    private const val BYTERANGES_HEADER = "multipart/byteranges; boundary=$BYTERANGES_BOUNDARY"
    private const val BYTERANGES_BOUNDARY_SEP = "--$BYTERANGES_BOUNDARY"
    private const val BYTERANGES_BOUNDARY_END = "--$BYTERANGES_BOUNDARY--"
    private const val NO_STORE = "no-store"
    private const val BUFFER_SIZE = 1 shl 16 // 64kb

    private val mimeMappings = MimeMappings(MimeMappings.DEFAULT).apply {
        add("yaml", YAML_MIME_TYPE)
        add("tgz", TGZ_MIME_TYPE)
        add("ico", ICO_MIME_TYPE)
    }

    fun response(filename: String, file: File) {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        val eTag = resolveETag(file)
        val disposition = encodeDisposition(filename)
        val mimeType = determineMediaType(filename)

        response.bufferSize = BUFFER_SIZE
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition)
        response.setHeader(HttpHeaders.ACCEPT_RANGES, BYTES)
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE)
        response.setHeader(HttpHeaders.ETAG, eTag)
        response.setDateHeader(HttpHeaders.LAST_MODIFIED, file.lastModified())
        response.characterEncoding = StandardCharsets.UTF_8.name()
        val rangeList: List<Range> = try {
            resolveRanges(request, file, eTag)
        } catch (exception: Exception) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */${file.length()}")
            response.status = HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE
            LoggerHolder.logBusinessException(exception, "Range response failed: range header is invalid.")
            return
        }
        try {
            when {
                rangeList.isEmpty() -> {
                    responseFullFile(file, response, mimeType)
                }
                rangeList.size == 1 -> {
                    responseSingleRange(file, response, mimeType, rangeList[0])
                }
                else -> {
                    responseMultiRange(file, response, mimeType, rangeList)
                }
            }
        } catch (exception: Exception) {
            if (exception.message.orEmpty().contains("Connection reset by peer")) {
                LoggerHolder.logBusinessException(exception, "Stream response failed: download abort by client.")
            } else {
                throw exception
            }
        }
    }

    private fun responseFullFile(file: File, response: HttpServletResponse, mimeType: String) {
        RandomAccessFile(file, "r").use {
            val output = response.outputStream
            val length = file.length()
            val range = Range(length)
            response.contentType = mimeType
            response.setHeader(HttpHeaders.CONTENT_LENGTH, range.getLength().toString())
            response.setHeader(HttpHeaders.CONTENT_RANGE, getRangeHeader(range))
            copyRange(it, output, range)
            output.flush()
        }
    }

    private fun responseSingleRange(file: File, response: HttpServletResponse, mimeType: String, range: Range) {
        RandomAccessFile(file, "r").use {
            val output = response.outputStream
            response.status = HttpServletResponse.SC_PARTIAL_CONTENT
            response.contentType = mimeType
            response.setHeader(HttpHeaders.CONTENT_LENGTH, range.getLength().toString())
            response.setHeader(HttpHeaders.CONTENT_RANGE, getRangeHeader(range))
            copyRange(it, output, range)
            output.flush()
        }
    }

    private fun responseMultiRange(file: File, response: HttpServletResponse, mimeType: String, rangeList: List<Range>) {
        RandomAccessFile(file, "r").use {
            val output = response.outputStream
            response.status = HttpServletResponse.SC_PARTIAL_CONTENT
            response.contentType = BYTERANGES_HEADER
            rangeList.forEach { range ->
                // write MIME header
                output.println()
                output.println(BYTERANGES_BOUNDARY_SEP)
                output.println(HttpHeaders.CONTENT_TYPE + ": " + mimeType)
                output.println(HttpHeaders.CONTENT_RANGE + ": " + getRangeHeader(range))
                output.println()

                copyRange(it, output, range)
            }
            output.println()
            output.print(BYTERANGES_BOUNDARY_END)
            output.flush()
        }
    }

    private fun copyRange(input: RandomAccessFile, output: OutputStream, range: Range) {
        if (range.getLength() == 0L) return
        input.seek(range.start)
        val length = range.getLength()
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesToRead = BUFFER_SIZE
        if (length < BUFFER_SIZE.toLong()) {
            bytesToRead = length.toInt()
        }
        var totalRead = 0L
        var read: Int = -1
        while (bytesToRead > 0 && input.read(buffer, 0, bytesToRead).also { read = it } != -1) {
            output.write(buffer, 0, read)
            totalRead += read.toLong()
            bytesToRead = (length - totalRead).coerceAtMost(BUFFER_SIZE.toLong()).toInt()
        }
    }

    private fun resolveRanges(request: HttpServletRequest, file: File, eTag: String?): List<Range> {
        val rangeList = mutableListOf<Range>()
        val fileLength = file.length()
        if (fileLength == 0L) {
            return rangeList
        }
        val rangeHeader = request.getHeader(HttpHeaders.RANGE) ?: return rangeList
        require(rangePatternRegex.matches(rangeHeader))
        val ifRangeHeader = request.getHeader(HttpHeaders.IF_RANGE)
        if (ifRangeHeader != null && ifRangeHeader != eTag) {
            val ifRangeValue = try {
                request.getDateHeader(HttpHeaders.IF_RANGE)
            } catch (e: Exception) {
                -1L
            }
            if (ifRangeValue != -1L && ifRangeValue + 1000 < file.lastModified()) {
                return rangeList
            }
        }
        for (part in rangeHeader.substring(6).split(",")) {
            require(part.isNotEmpty())
            val dashPos = part.indexOf(DASH)
            require(dashPos >= 0)
            val start = part.substring(0, dashPos).toLongOrNull()
            val end = part.substring(dashPos + 1, part.length).toLongOrNull()
            require(start != null || end != null)
            val range = Range(fileLength, start, end)
            range.takeIf { range.validate() }?.let { rangeList.add(it) }
        }
        return rangeList
    }

    private fun getRangeHeader(range: Range): String {
        return "bytes ${range.start}-${range.end}/${range.total}"
    }

    private fun determineMediaType(name: String): String {
        val extension = NodeUtils.getExtension(name).orEmpty()
        return mimeMappings.get(extension) ?: STREAM_MIME_TYPE
    }

    private fun encodeDisposition(filename: String): String {
        val encodeFilename = UriUtils.encode(filename, Charsets.UTF_8)
        return CONTENT_DISPOSITION_TEMPLATE.format(encodeFilename, encodeFilename)
    }

    private fun resolveETag(file: File): String? {
        val contentLength = file.length()
        val lastModified = file.lastModified()
        return if (contentLength >= 0 || lastModified >= 0) {
            "W/\"$contentLength-$lastModified\""
        } else null
    }
}
