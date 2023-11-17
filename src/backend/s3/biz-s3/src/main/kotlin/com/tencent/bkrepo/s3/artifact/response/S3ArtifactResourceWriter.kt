package com.tencent.bkrepo.s3.artifact.response

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.metrics.RecordAbleInputStream
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.resolve.response.DefaultArtifactResourceWriter
import com.tencent.bkrepo.common.artifact.stream.STREAM_BUFFER_SIZE
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.artifact.util.http.IOExceptionUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.springframework.beans.BeansException
import org.springframework.cloud.sleuth.Tracer
import org.springframework.http.HttpMethod
import org.springframework.util.unit.DataSize
import java.io.IOException
import java.io.OutputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * S3协议的响应输出
 */
class S3ArtifactResourceWriter (
    private val storageProperties: StorageProperties
) : DefaultArtifactResourceWriter(storageProperties) {

    @Throws(ArtifactResponseException::class)
    override fun write(resource: ArtifactResource): Throughput {
        if (resource.artifactMap.isEmpty() || resource.node==null) {
            writeEmptyArtifact()
        } else {
            responseRateLimitCheck()
            writeArtifact(resource)
        }
        return Throughput.EMPTY
    }

    /**
     * 响应空数据流，比如文件夹
     */
    private fun writeEmptyArtifact(): Throughput {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        response.setHeader("x-amz-request-id", getTraceId())
        response.setHeader("x-amz-trace-id", getTraceId())
        response.setHeader("Content-Type", MediaTypes.APPLICATION_OCTET_STREAM)
        response.setHeader("Content-Length", "0")
        response.setHeader("ETag", "")
        response.setCharacterEncoding("utf-8")
        response.addHeader("Server", HttpContextHolder.getClientAddress())
        response.status = HttpStatus.OK.value
        val outputStream: OutputStream = response.outputStream
        outputStream.close()
        return Throughput.EMPTY
    }

    /**
     * 响应构件数据流
     */
    private fun writeArtifact(resource: ArtifactResource): Throughput {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        val node = resource.node
        val range = resource.getSingleStream().range
        response.setHeader("x-amz-request-id", getTraceId())
        response.setHeader("x-amz-trace-id", getTraceId())
        response.setHeader("Content-Type", resource.contentType?:MediaTypes.TEXT_PLAIN)
        response.setHeader("Content-Length", resource.getTotalSize().toString())
        response.setHeader("ETag", node?.sha256!!)
        response.setCharacterEncoding(resource.characterEncoding)
        response.addHeader("Server", HttpContextHolder.getClientAddress())
        response.status = resource.status?.value ?: HttpStatus.OK.value
        response.bufferSize = getBufferSize(range.length.toInt())

        return writeRangeStream(resource, request, response)
    }


    /**
     * 当仓库配置下载限速小于等于最低限速时则直接将请求断开, 避免占用过多连接
     */
    private fun responseRateLimitCheck() {
        val rateLimitOfRepo = ArtifactContextHolder.getRateLimitOfRepo()
        if (rateLimitOfRepo.responseRateLimit != DataSize.ofBytes(-1) &&
            rateLimitOfRepo.responseRateLimit <= storageProperties.response.circuitBreakerThreshold) {
            throw TooManyRequestsException(
                "The circuit breaker is activated when too many download requests are made to the service!"
            )
        }
    }

    /**
     * 将数据流以Range方式写入响应
     */
    private fun writeRangeStream(
        resource: ArtifactResource,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Throughput {
        val inputStream = resource.getSingleStream()
        if (request.method == HttpMethod.HEAD.name) {
            return Throughput.EMPTY
        }
        val recordAbleInputStream = RecordAbleInputStream(inputStream)
        try {
            return measureThroughput {
                recordAbleInputStream.rateLimit(responseRateLimitWrapper(storageProperties.response.rateLimit)).use {
                    it.copyTo(
                        out = response.outputStream,
                        bufferSize = getBufferSize(inputStream.range.length.toInt())
                    )
                }
            }
        } catch (exception: IOException) {
            val message = exception.message.orEmpty()
            val status = if (IOExceptionUtils.isClientBroken(exception)) HttpStatus.BAD_REQUEST else HttpStatus.INTERNAL_SERVER_ERROR
            throw ArtifactResponseException(message, status)
        }
    }

    /**
     * 将仓库级别的限速配置导入
     * 当同时存在全局限速配置以及仓库级别限速配置时，以仓库级别配置优先
     */
    private fun responseRateLimitWrapper(rateLimit: DataSize): Long {
        val rateLimitOfRepo = ArtifactContextHolder.getRateLimitOfRepo()
        if (rateLimitOfRepo.responseRateLimit != DataSize.ofBytes(-1)) {
            return rateLimitOfRepo.responseRateLimit.toBytes()
        }
        return rateLimit.toBytes()
    }

    /**
     * 获取动态buffer size
     * @param totalSize 数据总大小
     */
    private fun getBufferSize(totalSize: Int): Int {
        val bufferSize = storageProperties.response.bufferSize.toBytes().toInt()
        if (bufferSize < 0 || totalSize < 0) {
            return STREAM_BUFFER_SIZE
        }
        return if (totalSize < bufferSize) totalSize else bufferSize
    }

    /**
     * 请求id
     */
    private fun getTraceId(): String? {
        return try {
            SpringContextUtils.getBean<Tracer>().currentSpan()?.context()?.traceId()
        } catch (_: BeansException) {
            null
        }
    }

}