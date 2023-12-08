/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.s3.artifact.response

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.metrics.RecordAbleInputStream
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResourceWriter
import com.tencent.bkrepo.common.artifact.resolve.response.BaseArtifactResourceHandler
import com.tencent.bkrepo.common.artifact.resolve.response.DefaultArtifactResourceWriter
import com.tencent.bkrepo.common.artifact.stream.STREAM_BUFFER_SIZE
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.artifact.util.http.IOExceptionUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.s3.artifact.utils.ContextUtil
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
) : BaseArtifactResourceHandler(storageProperties), ArtifactResourceWriter {

    @Throws(ArtifactResponseException::class)
    override fun write(resource: ArtifactResource): Throughput {
        return if (resource.artifactMap.isEmpty() || resource.node == null) {
            writeEmptyArtifact()
        } else {
            responseRateLimitCheck()
            writeArtifact(resource)
        }
    }

    private fun writeEmptyArtifact(): Throughput {
        val response = HttpContextHolder.getResponse()
        prepareResponseHeaders(response, 0, "", HttpStatus.OK.value)
        return Throughput.EMPTY
    }

    private fun writeArtifact(resource: ArtifactResource): Throughput {
        val response = HttpContextHolder.getResponse()
        val request = HttpContextHolder.getRequest()
        val node = resource.node
        val range = resource.getSingleStream().range
        val contentType = resource.contentType ?: MediaTypes.TEXT_PLAIN
        val characterEncoding = resource.characterEncoding
        val status = resource.status?.value ?: HttpStatus.OK.value
        val totalSize = resource.getTotalSize().toString()

        prepareResponseHeaders(response, totalSize.toLong(), node?.sha256!!, status, contentType, characterEncoding)
        response.bufferSize = getBufferSize(range.length.toInt())
        return writeRangeStream(resource, request, response)
    }

    private fun prepareResponseHeaders(
        response: HttpServletResponse,
        contentLength: Long,
        eTag: String,
        status: Int,
        contentType: String = MediaTypes.APPLICATION_OCTET_STREAM,
        characterEncoding: String = "utf-8"
    ) {
        response.setHeader("x-amz-request-id", ContextUtil.getTraceId())
        response.setHeader("x-amz-trace-id", ContextUtil.getTraceId())
        response.setHeader("Content-Type", contentType)
        response.setHeader("Content-Length", contentLength.toString())
        response.setHeader("ETag", eTag)
        response.setCharacterEncoding(characterEncoding)
        response.status = status
    }
}