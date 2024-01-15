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

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.resolve.response.AbstractArtifactResourceHandler
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.s3.artifact.utils.ContextUtil
import com.tencent.bkrepo.s3.constant.DEFAULT_ENCODING
import com.tencent.bkrepo.s3.constant.S3HttpHeaders
import com.tencent.bkrepo.s3.utils.TimeUtil
import javax.servlet.http.HttpServletResponse

/**
 * S3协议的响应输出
 */
class S3ArtifactResourceWriter (
    storageProperties: StorageProperties
) : AbstractArtifactResourceHandler(storageProperties) {

    @Throws(ArtifactResponseException::class)
    override fun write(resource: ArtifactResource): Throughput {
        responseRateLimitCheck()
        return writeArtifact(resource)
    }

    private fun writeArtifact(resource: ArtifactResource): Throughput {
        val response = HttpContextHolder.getResponse()
        val request = HttpContextHolder.getRequest()
        val node = resource.node
        val range = resource.getSingleStream().range
        val contentType = resource.contentType ?: MediaTypes.APPLICATION_OCTET_STREAM
        val characterEncoding = resource.characterEncoding
        val status = resource.status?.value ?: resolveStatus(request)
        val totalSize = resource.getTotalSize().toString()

        prepareResponseHeaders(response, totalSize.toLong(), node, status, contentType, characterEncoding, range)
        response.bufferSize = getBufferSize(range.length.toInt())
        return writeRangeStream(resource, request, response)
    }

    private fun prepareResponseHeaders(
        response: HttpServletResponse,
        contentLength: Long,
        node: NodeDetail?,
        status: Int,
        contentType: String = MediaTypes.APPLICATION_OCTET_STREAM,
        characterEncoding: String = DEFAULT_ENCODING,
        range: Range
    ) {
        response.setHeader(HttpHeaders.ACCEPT_RANGES, StringPool.BYTES)
        response.setHeader(HttpHeaders.CONTENT_RANGE, "${StringPool.BYTES} $range")
        response.setHeader(S3HttpHeaders.X_AMZ_REQUEST_ID, ContextUtil.getTraceId())
        response.setHeader(S3HttpHeaders.X_AMZ_TRACE_ID, ContextUtil.getTraceId())
        response.setHeader(HttpHeaders.CONTENT_TYPE, contentType)
        response.setHeader(HttpHeaders.CONTENT_LENGTH, contentLength.toString())
        node?.let {
            response.setHeader(HttpHeaders.ETAG, "\"${it.md5}\"")
            // 本地时间转换为GMT时间
            response.setHeader(HttpHeaders.LAST_MODIFIED, TimeUtil.getLastModified(it))
        }
        response.characterEncoding = characterEncoding
        response.status = status
    }
}
