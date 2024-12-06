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

package com.tencent.bkrepo.preview.config

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.metrics.RecordAbleInputStream
import com.tencent.bkrepo.common.artifact.resolve.response.AbstractArtifactResourceHandler
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.preview.constant.PREVIEW_TMP_FILE_SAVE_PATH
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * preview制品响应输出，下载到临时目录
 */
class PreviewArtifactResourceWriter(
    storageProperties: StorageProperties,
    requestLimitCheckService: RequestLimitCheckService
) : AbstractArtifactResourceHandler(
    storageProperties, requestLimitCheckService
) {

    private val storageProperties = storageProperties
    private val requestLimitCheckService = requestLimitCheckService

    @Throws(ArtifactResponseException::class, OverloadException::class)
    override fun write(resource: ArtifactResource): Throughput {
        responseRateLimitCheck()
        downloadRateLimitCheck(resource)
        return writeArtifact(resource)
    }

    private fun writeArtifact(resource: ArtifactResource): Throughput {
        val request = HttpContextHolder.getRequest()
        var filePath = request.getAttribute(PREVIEW_TMP_FILE_SAVE_PATH)
        val inputStream = resource.getSingleStream()
        val length = inputStream.range.length
        var rateLimitFlag = false
        var exp: Exception? = null
        val recordAbleInputStream = RecordAbleInputStream(inputStream)

        try {
            return measureThroughput {
                val stream = requestLimitCheckService.bandwidthCheck(
                    recordAbleInputStream, storageProperties.response.circuitBreakerThreshold, length
                ) ?: recordAbleInputStream.rateLimit(
                    responseRateLimitWrapper(storageProperties.response.rateLimit)
                )
                rateLimitFlag = stream is CommonRateLimitInputStream

                stream.use { input ->
                    // 写入到临时文件
                    File(filePath!!.toString()).outputStream().use { fileOutput ->
                        input.copyTo(fileOutput, bufferSize = getBufferSize(length))
                    }
                }
            }
        } catch (exception: IOException) {
            logger.error("Failed to write artifacts to the temporary directory [$filePath]", exception)
            exp = exception
            throw SystemErrorException()
        } catch (overloadEx: OverloadException) {
            logger.error("Current limit is exceeded", overloadEx)
            exp = overloadEx
            throw overloadEx
        } finally {
            if (rateLimitFlag) {
                requestLimitCheckService.bandwidthFinish(exp)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PreviewArtifactResourceWriter::class.java)
    }
}
