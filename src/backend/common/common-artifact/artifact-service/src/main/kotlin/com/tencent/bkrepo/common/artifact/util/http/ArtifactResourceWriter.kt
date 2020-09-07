package com.tencent.bkrepo.common.artifact.util.http

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool.BYTES
import com.tencent.bkrepo.common.api.constant.StringPool.NO_CACHE
import com.tencent.bkrepo.common.api.util.executeAndMeasureNanoTime
import com.tencent.bkrepo.common.artifact.constant.CONTENT_DISPOSITION_TEMPLATE
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_DOWNLOADED_BYTES_COUNT
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_DOWNLOADED_CONSUME_COUNT
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.STREAM_BUFFER_SIZE
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import org.springframework.boot.web.server.MimeMappings
import org.springframework.http.HttpMethod
import org.springframework.web.util.UriUtils
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object ArtifactResourceWriter {

    private val logger = LoggerFactory.getLogger(ArtifactResourceWriter::class.java)

    private val mimeMappings = MimeMappings(MimeMappings.DEFAULT).apply {
        add("yaml", MediaTypes.APPLICATION_YAML)
        add("tgz", MediaTypes.APPLICATION_TGZ)
        add("ico", MediaTypes.APPLICATION_ICO)
    }

    fun write(resource: ArtifactResource) {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        val artifact = resource.artifact
        val node = resource.node
        val range = resource.inputStream.range

        response.bufferSize = STREAM_BUFFER_SIZE
        response.characterEncoding = resource.characterEncoding
        response.contentType = determineMediaType(artifact)
        response.status = resolveStatus(request)
        response.setHeader(HttpHeaders.ACCEPT_RANGES, BYTES)
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_CACHE)
        response.setHeader(HttpHeaders.CONTENT_LENGTH, resolveContentLength(range))
        response.setHeader(HttpHeaders.CONTENT_RANGE, resolveContentRange(range))
        if (resource.useDisposition) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, encodeDisposition(artifact))
        }
        node?.let {
            response.setHeader(HttpHeaders.ETAG, resolveETag(it))
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, resolveLastModified(it.lastModifiedDate))
        }

        try {
            resource.inputStream.use {
                if (request.method != HttpMethod.HEAD.name) {
                    writeRangeStream(it, response)
                } else {
                    logger.info("Skip writing data to response body because of HEAD request")
                }
            }
        } catch (exception: IOException) {
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
        return if (isRangeRequest) HttpStatus.PARTIAL_CONTENT.value else HttpStatus.OK.value
    }

    private fun resolveContentLength(range: Range): String {
        return range.length.toString()
    }

    private fun resolveContentRange(range: Range): String {
        return "$BYTES $range"
    }

    private fun resolveLastModified(lastModifiedDate: String): Long {
        val localDateTime = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
        return localDateTime.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
    }

    private fun writeRangeStream(inputStream: ArtifactInputStream, response: HttpServletResponse) {
        executeAndMeasureNanoTime {
            inputStream.copyTo(response.outputStream, STREAM_BUFFER_SIZE)
        }.apply {
            val throughput = Throughput(first, second)
            Metrics.counter(ARTIFACT_DOWNLOADED_BYTES_COUNT).increment(throughput.bytes.toDouble())
            Metrics.counter(ARTIFACT_DOWNLOADED_CONSUME_COUNT).increment(throughput.duration.toMillis().toDouble())
            logger.info("Response artifact file, $throughput.")
        }
    }

    private fun determineMediaType(name: String): String {
        val extension = PathUtils.resolveExtension(name).orEmpty()
        return mimeMappings.get(extension) ?: MediaTypes.APPLICATION_OCTET_STREAM
    }

    private fun encodeDisposition(filename: String): String {
        val encodeFilename = UriUtils.encode(filename, Charsets.UTF_8)
        return CONTENT_DISPOSITION_TEMPLATE.format(encodeFilename, encodeFilename)
    }

    private fun resolveETag(node: NodeDetail): String {
        return node.sha256!!
    }
}
