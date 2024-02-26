/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.base.interceptor

import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_LENGTH
import com.tencent.bkrepo.common.api.constant.HttpHeaders.CONTENT_RANGE
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.chunked.ChunkedUploadUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.constant.CHUNKED_UPLOAD
import com.tencent.bkrepo.replication.constant.REPOSITORY_INFO
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.SIZE
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.util.StreamRequestBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * 只针对分块上传流程中的请求失败进行重试
 */
class RetryInterceptor : Interceptor {

    private val localDataManager by lazy { SpringContextUtils.getBean<LocalDataManager>() }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request: Request = chain.request()
        var response: Response? = null
        var responseOK = false
        var tryCount = 0
        var enableRetry = true

        while (!responseOK && tryCount < 3 && enableRetry) {
            try {
                response = chain.proceed(request)
                // 针对429返回需要做延时重试
                responseOK = if (response.code in retryCode) {
                    Thread.sleep(500)
                    false
                } else {
                    response.code in 200..499
                }
            } catch (e: Exception) {
                logger.warn(
                    "The result of request ${request.url} is failure and error is ${e.message}", e
                )
                // 只针对分块上传的所有请求进行重试
                if (request.header(CHUNKED_UPLOAD) == null) {
                    enableRetry = false
                }
                // 如果第2次重试还是失败，抛出失败异常
                if (tryCount == 2 || !enableRetry) throw e
            } finally {
                // 只针对分块上传的所有请求进行重试
                if (request.header(CHUNKED_UPLOAD) == null) {
                    enableRetry = false
                }
                if (!responseOK && tryCount < 2 && enableRetry) {
                    logger.warn(
                        "The result of request ${request.url} is failure and code is ${response?.code}" +
                            ", will retry it - $tryCount"
                    )
                    response?.close()
                    request = buildRetryRequestBody(request)
                }
                tryCount++
            }
        }
        return response!!
    }

    private fun buildRetryRequestBody(request: Request): Request {
        val (projectId, repoName) = getRepoFromHeader(request)
        if (projectId.isNullOrEmpty()) return request
        if (repoName.isNullOrEmpty()) return request
        val sha256 = getSha256FromHeader(request)
        if (sha256.isNullOrEmpty()) return request
        val contentLength = getSizeFromHeader(request) ?: return request
        val size = getFileLengthFromHeader(request) ?: return request
        val rangeStr = getContentRangeFromHeader(request)
        logger.info("range info is $rangeStr and size is $size")
        if (rangeStr.isNullOrEmpty()) return request
        val (start, end) = ChunkedUploadUtils.getRangeInfo(rangeStr)
        val range = Range(start, end, size)
        val retryBody = StreamRequestBody(
            localDataManager.loadInputStreamByRange(sha256, range, projectId, repoName),
            contentLength
        )
        return request.newBuilder().method(request.method, retryBody).build()
    }

    /**
     * 从请求头中获取对应项目、仓库信息
     */
    private fun getRepoFromHeader(request: Request): Pair<String?, String?> {
        return request.header(REPOSITORY_INFO)?.let {
            val list = it.split("|")
            Pair(list.first(), list.last())
        } ?: Pair(null, null)
    }

    /**
     * 从请求头中获取对于sha256信息
     */
    private fun getSha256FromHeader(request: Request): String? {
        return request.header(SHA256)
    }

    /**
     * 从请求头中获取对于content-range信息
     */
    private fun getContentRangeFromHeader(request: Request): String? {
        return request.header(CONTENT_RANGE)
    }

    /**
     * 从请求头中获取对于当前请求体大小信息
     */
    private fun getSizeFromHeader(request: Request): Long? {
        return request.header(CONTENT_LENGTH)?.toLongOrNull()
    }

    /**
     * 从请求头中获取对于文件大小信息
     */
    private fun getFileLengthFromHeader(request: Request): Long? {
        return request.header(SIZE)?.toLongOrNull()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RetryInterceptor::class.java)
        private val retryCode = listOf(429, 408)
    }
}
