/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.metrics.RecordAbleInputStream
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.artifact.util.http.IOExceptionUtils
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.util.unit.DataSize
import java.io.IOException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


abstract class AbstractArtifactResourceHandler(
    private val storageProperties: StorageProperties,
    private val requestLimitCheckService: RequestLimitCheckService
) : ArtifactResourceWriter {
    /**
     * 获取动态buffer size
     * @param totalSize 数据总大小
     */
    protected fun getBufferSize(totalSize: Long): Int {
        if (totalSize == 0L) {
            // buffer size 不能为0，否则会导致流io空转。
            return 1
        }
        val bufferSize = storageProperties.response.bufferSize.toBytes().toInt()
        require(totalSize > 0 && bufferSize > 0)
        return totalSize.coerceAtMost(bufferSize.toLong()).toInt()
    }

    /**
     * 将仓库级别的限速配置导入
     * 当同时存在全局限速配置以及仓库级别限速配置时，以仓库级别配置优先
     */
    protected fun responseRateLimitWrapper(rateLimit: DataSize): Long {
        val rateLimitOfRepo = ArtifactContextHolder.getRateLimitOfRepo()
        if (rateLimitOfRepo.responseRateLimit != DataSize.ofBytes(-1)) {
            return rateLimitOfRepo.responseRateLimit.toBytes()
        }
        return rateLimit.toBytes()
    }

    /**
     * 当仓库配置下载限速小于等于最低限速时则直接将请求断开, 避免占用过多连接
     */
    protected fun responseRateLimitCheck() {
        val rateLimitOfRepo = ArtifactContextHolder.getRateLimitOfRepo()
        if (rateLimitOfRepo.responseRateLimit != DataSize.ofBytes(-1) &&
            rateLimitOfRepo.responseRateLimit <= storageProperties.response.circuitBreakerThreshold) {
            throw TooManyRequestsException(
                "The circuit breaker is activated when too many download requests are made to the service!"
            )
        }
    }

    /**
     * 当仓库配置下载限速小于等于最低限速时则直接将请求断开, 避免占用过多连接
     */
    protected fun downloadRateLimitCheck(resource: ArtifactResource) {
        val applyPermits = resource.getTotalSize()
        requestLimitCheckService.postLimitCheck(applyPermits)
    }

    /**
     * 将数据流以Range方式写入响应
     */
    protected fun writeRangeStream(
        resource: ArtifactResource,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Throughput {
        val inputStream = resource.getSingleStream()
        if (request.method == HttpMethod.HEAD.name) {
            return Throughput.EMPTY
        }
        val length = inputStream.range.length
        var rateLimitFlag = false
        var exp: Exception? = null
        val recordAbleInputStream = RecordAbleInputStream(inputStream)
        try {
            return measureThroughput {
                val stream = requestLimitCheckService.bandwidthCheck(
                    recordAbleInputStream, storageProperties.response.circuitBreakerThreshold,
                    length
                ) ?: recordAbleInputStream.rateLimit(
                    responseRateLimitWrapper(storageProperties.response.rateLimit)
                )
                rateLimitFlag = stream is CommonRateLimitInputStream
                stream.use {
                    it.copyTo(
                        out = response.outputStream,
                        bufferSize = getBufferSize(length)
                    )
                }
            }
        } catch (exception: IOException) {
            // 直接向上抛IOException经过CglibAopProxy会抛java.lang.reflect.UndeclaredThrowableException: null
            // 由于已经设置了Content-Type为application/octet-stream, spring找不到对应的Converter，导致抛
            // org.springframework.http.converter.HttpMessageNotWritableException异常，会重定向到/error页面
            // 又因为/error页面不存在，最终返回404，所以要对IOException进行包装，在上一层捕捉处理
            val message = exception.message.orEmpty()
            val status = if (IOExceptionUtils.isClientBroken(exception)) {
                HttpStatus.BAD_REQUEST
            } else {
                logger.warn("write range stream failed", exception)
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
        }
    }

    /**
     * 解析响应状态
     */
    protected fun resolveStatus(request: HttpServletRequest): Int {
        val isRangeRequest = request.getHeader(HttpHeaders.RANGE)?.isNotBlank() ?: false
        return if (isRangeRequest) HttpStatus.PARTIAL_CONTENT.value else HttpStatus.OK.value
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractArtifactResourceHandler::class.java)
    }
}
