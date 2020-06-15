package com.tencent.bkrepo.common.artifact.util.response

import com.tencent.bkrepo.common.api.util.executeAndMeasureNanoTime
import com.tencent.bkrepo.common.artifact.config.BYTES
import com.tencent.bkrepo.common.artifact.config.CONTENT_DISPOSITION_TEMPLATE
import com.tencent.bkrepo.common.artifact.config.ICO_MIME_TYPE
import com.tencent.bkrepo.common.artifact.config.STREAM_MIME_TYPE
import com.tencent.bkrepo.common.artifact.config.TGZ_MIME_TYPE
import com.tencent.bkrepo.common.artifact.config.YAML_MIME_TYPE
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_DOWNLOADED_BYTES_COUNT
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_DOWNLOADED_CONSUME_COUNT
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.util.NodeUtils
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.springframework.boot.web.server.MimeMappings
import org.springframework.http.HttpHeaders
import org.springframework.web.util.UriUtils
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object ArtifactResourceWriter {

    private val logger = LoggerFactory.getLogger(ArtifactResourceWriter::class.java)

    private const val NO_CACHE = "no-cache"
    private const val BUFFER_SIZE = 8 * 1024

    private val mimeMappings = MimeMappings(MimeMappings.DEFAULT).apply {
        add("yaml", YAML_MIME_TYPE)
        add("tgz", TGZ_MIME_TYPE)
        add("ico", ICO_MIME_TYPE)
    }

    fun write(resource: ArtifactResource) {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        val artifact = resource.artifact
        val node = resource.nodeInfo
        val range = resource.inputStream.range

        response.bufferSize = BUFFER_SIZE
        response.characterEncoding = resource.characterEncoding
        response.contentType = determineMediaType(artifact)
        response.status = resolveStatus(request)
        response.setHeader(HttpHeaders.ACCEPT_RANGES, BYTES)
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, encodeDisposition(artifact))
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_CACHE)
        node?.let {
            response.setHeader(HttpHeaders.ETAG, resolveETag(node))
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, resolveLastModified(node.lastModifiedDate))
        }
        response.setHeader(HttpHeaders.CONTENT_LENGTH, resolveContentLength(range))
        response.setHeader(HttpHeaders.CONTENT_RANGE, resolveContentRange(range))

        try {
            writeRangeStream(resource.inputStream, response)
            response.flushBuffer()
        } catch (exception: Exception) {
            val message = exception.message.orEmpty()
            when {
                message.contains("Connection reset by peer") -> {
                    LoggerHolder.logBusinessException(exception, "Stream response failed[Connection reset by peer]")
                }
                message.contains("Broken pipe") -> {
                    LoggerHolder.logBusinessException(exception, "Stream response failed[Broken pipe]")
                }
                else -> throw exception
            }
        }
    }

    private fun resolveStatus(request: HttpServletRequest): Int {
        val isRangeRequest = request.getHeader(HttpHeaders.RANGE)?.isNotBlank() ?: false
        return if (isRangeRequest) HttpServletResponse.SC_PARTIAL_CONTENT else HttpServletResponse.SC_OK
    }

    private fun resolveContentLength(range: Range): String {
        return range.length.toString()
    }

    private fun resolveContentRange(range: Range): String {
        return "bytes ${range.start}-${range.end}/${range.total}"
    }

    private fun resolveLastModified(lastModifiedDate: String): Long {
        val localDateTime = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
        return localDateTime.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
    }

    private fun writeRangeStream(inputStream: ArtifactInputStream, response: HttpServletResponse) {
        inputStream.use {
            val output = response.outputStream
            executeAndMeasureNanoTime {
                inputStream.copyTo(output, BUFFER_SIZE)
            }.apply {
                val throughput = Throughput(first, second)
                Metrics.counter(ARTIFACT_DOWNLOADED_BYTES_COUNT).increment(throughput.bytes.toDouble())
                Metrics.counter(ARTIFACT_DOWNLOADED_CONSUME_COUNT).increment(throughput.duration.toMillis().toDouble())
                logger.info("Response artifact file, $throughput.")
            }
            output.flush()
        }
    }

    private fun determineMediaType(name: String): String {
        val extension = NodeUtils.getExtension(name).orEmpty()
        return mimeMappings.get(extension) ?: STREAM_MIME_TYPE
    }

    private fun encodeDisposition(filename: String): String {
        val encodeFilename = UriUtils.encode(filename, Charsets.UTF_8)
        return CONTENT_DISPOSITION_TEMPLATE.format(encodeFilename, encodeFilename)
    }

    private fun resolveETag(node: NodeInfo): String {
        return node.sha256!!
    }
}
