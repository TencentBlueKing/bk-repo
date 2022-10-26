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
import com.tencent.bkrepo.replication.constant.REPOSITORY_INFO
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.util.StreamRequestBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * 针对特定请求失败进行重试
 */
class RetryInterceptor(
    private val localDataManager: LocalDataManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request: Request = chain.request()
        var response: Response? = null
        var responseOK = false
        var tryCount = 0

        while (!responseOK && tryCount < 3) {
            try {
                response = chain.proceed(request)
                // 针对429返回需要做延时重试
                responseOK = if (response.code() == 429) {
                    Thread.sleep(500)
                    false
                } else {
                    response.code() in 200..499
                }
            } catch (e: Exception) {
                logger.warn(
                    "The result of request ${request.url()} is failure and error is ${e.message}"
                )
                // 如果第2次重试还是失败，抛出失败异常
                if (tryCount == 2) throw e
            } finally {
                if (!responseOK && tryCount < 2) {
                    logger.warn(
                        "The result of request ${request.url()} is failure and code is ${response?.code()}" +
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
        val sha256 = getSha256FromHeader(request)
        val size = getSizeFromHeader(request)
        if (projectId.isNullOrEmpty()) return request
        if (repoName.isNullOrEmpty()) return request
        if (sha256.isNullOrEmpty()) return request
        if (size == null) return request
        val retryBody = StreamRequestBody(localDataManager.loadInputStream(sha256, size, projectId, repoName), size)
        return request.newBuilder().method(request.method(), retryBody).build()
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
     * 从请求头中获取对于文件大小信息
     */
    private fun getSizeFromHeader(request: Request): Long? {
        return request.header(CONTENT_LENGTH)?.toLong()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RetryInterceptor::class.java)
    }
}
