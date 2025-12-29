/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.resolve.response

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_CRC64ECMA
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_MD5
import com.tencent.bkrepo.common.artifact.constant.X_CHECKSUM_SHA256
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.metrics.RecordAbleInputStream
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.artifact.util.http.HttpHeaderUtils.determineMediaType
import com.tencent.bkrepo.common.artifact.util.http.HttpHeaderUtils.encodeDisposition
import com.tencent.bkrepo.common.artifact.util.http.IOExceptionUtils.isClientBroken
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import com.tencent.bkrepo.common.service.otel.util.TraceHeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ArtifactResourceWriter默认实现
 */
open class DefaultArtifactResourceWriter(
    private val storageProperties: StorageProperties,
    private val requestLimitCheckService: RequestLimitCheckService
) : AbstractArtifactResourceHandler(
    storageProperties, requestLimitCheckService
) {

    @Throws(ArtifactResponseException::class, OverloadException::class)
    override fun write(resource: ArtifactResource): Throughput {
        responseRateLimitCheck()
        downloadRateLimitCheck(resource)
        TraceHeaderUtils.setResponseHeader()
        return if (resource.containsMultiArtifact()) {
            writeMultiArtifact(resource)
        } else {
            writeSingleArtifact(resource)
        }
    }

    /**
     * 响应单个构件数据
     * 单个响应流支持range下载
     */
    private fun writeSingleArtifact(resource: ArtifactResource): Throughput {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        val name = resource.getSingleName()
        val range = resource.getSingleStream().range
        val cacheControl = resource.node?.metadata?.get(HttpHeaders.CACHE_CONTROL)?.toString()
            ?: resource.node?.metadata?.get(HttpHeaders.CACHE_CONTROL.lowercase(Locale.getDefault()))?.toString()
            ?: StringPool.NO_CACHE

        response.bufferSize = getBufferSize(range.length)
        val mediaType = resource.contentType ?: determineMediaType(name, storageProperties.response.mimeMappings)
        response.characterEncoding = determineCharset(mediaType, resource.characterEncoding)
        response.contentType = mediaType
        response.status = resource.status?.value ?: resolveStatus(request)
        response.setContentLengthLong(range.length)
        response.setHeader(HttpHeaders.ACCEPT_RANGES, StringPool.BYTES)
        response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl)
        response.setHeader(HttpHeaders.CONTENT_RANGE, resolveContentRange(range))
        if (resource.useDisposition) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, encodeDisposition(name))
        }

        resource.node?.let {
            response.setHeader(HttpHeaders.ETAG, resolveETag(it))
            response.setHeader(X_CHECKSUM_MD5, it.md5)
            response.setHeader(X_CHECKSUM_SHA256, it.sha256)
            it.crc64ecma?.let { crc64 -> response.setHeader(X_CHECKSUM_CRC64ECMA, crc64) }
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, resolveLastModified(it.lastModifiedDate))
        }

        setCustomHeader(response, resource)
        return writeRangeStream(resource, request, response)
    }

    open fun setCustomHeader(response: HttpServletResponse, resource: ArtifactResource) {

    }

    /**
     * 响应多个构件数据，将以ZipOutputStream方式输出
     * 不支持Range下载
     * 不支持E-TAG和X_CHECKSUM_MD5头
     * 下载前无法预知content-length，将以Transfer-Encoding: chunked下载，某些下载工具或者浏览器的显示进度可能存在问题
     */
    private fun writeMultiArtifact(resource: ArtifactResource): Throughput {
        val request = HttpContextHolder.getRequest()
        val response = HttpContextHolder.getResponse()
        val name = resolveMultiArtifactName(resource)

        response.bufferSize = getBufferSize(resource.getTotalSize())
        response.characterEncoding = resource.characterEncoding
        response.contentType = determineMediaType(name, storageProperties.response.mimeMappings)
        response.status = HttpStatus.OK.value
        response.setHeader(HttpHeaders.CACHE_CONTROL, StringPool.NO_CACHE)
        if (resource.useDisposition) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, encodeDisposition(name))
        }
        resource.node?.let {
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, resolveLastModified(it.lastModifiedDate))
        }
        return writeZipStream(resource, request, response)
    }

    /**
     * 响应多个构件时解析构件名称
     */
    private fun resolveMultiArtifactName(resource: ArtifactResource): String {
        val baseName = when {
            resource.node == null -> System.currentTimeMillis().toString()
            PathUtils.isRoot(resource.node.name) -> resource.node.projectId + "-" + resource.node.repoName
            else -> resource.node.name
        }
        return "$baseName.zip"
    }

    /**
     * 解析content range
     */
    private fun resolveContentRange(range: Range): String {
        return "${StringPool.BYTES} $range"
    }

    /**
     * 解析last modified
     */
    private fun resolveLastModified(lastModifiedDate: String): Long {
        val localDateTime = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
        return localDateTime.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * 将数据流以ZipOutputStream方式写入响应
     */
    private fun writeZipStream(
        resource: ArtifactResource,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Throughput {
        if (request.method == HttpMethod.HEAD.name()) {
            return Throughput.EMPTY
        }
        var rateLimitFlag = false
        var exp: Exception? = null
        try {
            return measureThroughput {
                val zipOutput = ZipOutputStream(response.outputStream.buffered())
                zipOutput.setMethod(ZipOutputStream.DEFLATED)
                zipOutput.use {
                    resource.artifactMap.forEach { (name, inputStream) ->
                        val recordAbleInputStream = RecordAbleInputStream(inputStream)
                        zipOutput.putNextEntry(generateZipEntry(name, inputStream))
                        val stream = requestLimitCheckService.bandwidthCheck(
                            recordAbleInputStream, storageProperties.response.circuitBreakerThreshold,
                            inputStream.range.length
                        ) ?: recordAbleInputStream.rateLimit(
                            responseRateLimitWrapper(storageProperties.response.rateLimit)
                        )
                        rateLimitFlag = stream is CommonRateLimitInputStream
                        stream.use {
                            it.copyTo(
                                out = zipOutput,
                                bufferSize = getBufferSize(inputStream.range.length)
                            )
                        }
                        zipOutput.closeEntry()
                    }
                }
                resource.getTotalSize()
            }
        } catch (exception: IOException) {
            val message = exception.message.orEmpty()
            val status = if (isClientBroken(exception)) {
                HttpStatus.BAD_REQUEST
            } else {
                logger.warn("write zip stream failed", exception)
                HttpStatus.INTERNAL_SERVER_ERROR
            }
            exp = exception
            throw ArtifactResponseException(message, status)
        } catch (overloadEx: OverloadException) {
            exp = overloadEx
            throw overloadEx
        } finally {
            if (rateLimitFlag) {
                requestLimitCheckService.bandwidthFinish(exp)
            }
            resource.artifactMap.values.forEach { it.closeQuietly() }
        }
    }

    /**
     * 解析e-tag
     */
    private fun resolveETag(node: NodeDetail): String {
        return node.sha256!!
    }

    /**
     * 根据[artifactName]生成ZipEntry
     */
    private fun generateZipEntry(artifactName: String, inputStream: ArtifactInputStream): ZipEntry {
        val entry = ZipEntry(artifactName)
        entry.size = inputStream.range.length
        return entry
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultArtifactResourceWriter::class.java)
    }
}
