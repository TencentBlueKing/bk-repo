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

package com.tencent.bkrepo.replication.replica.base.impl.remote.base

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.replication.pojo.docker.OciErrorResponse
import com.tencent.bkrepo.replication.pojo.remote.DefaultHandlerResult
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.replica.base.impl.remote.exception.ArtifactPushException
import com.tencent.bkrepo.replication.util.HttpUtils
import okhttp3.OkHttpClient
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * 默认请求处理类
 */
class DefaultHandler(
    private val httpClient: OkHttpClient,
    private val ignoredFailureCode: List<Int> = emptyList(),
    private val extraSuccessCode: List<Int> = emptyList()
) {
    // 请求各种参数属性
    lateinit var requestProperty: RequestProperty

    /**
     * 对传入请求进行处理判断
     */
    fun process(): DefaultHandlerResult {
        val request = HttpUtils.wrapperRequest(requestProperty)
        val response = httpClient.newCall(request).execute()
        response.use {
            return when {
                isSuccess(it) -> {
                    logger.info("${Thread.currentThread().name} Result of the request ${request.url()} is success")
                    wrapperSuccess(it)
                }
                isFailure(it) -> {
                    logger.info("${Thread.currentThread().name} Result of the request ${request.url()} is failure")
                    wrapperFailure(it)
                }
                else -> {
                    val error = JsonUtils.objectMapper.readValue(
                        response.body()!!.byteStream(), Map::class.java
                    )?.toJsonString()
                    throw ArtifactPushException(
                        "invalid response  ${it.code()} for request ${it.request().url()}, error is $error"
                    )
                }
            }
        }
    }

    /**
     * 判断请求是否成功
     */
    private fun isSuccess(response: Response): Boolean {
        return response.isSuccessful || extraSuccessCode.contains(response.code())
    }

    /**
     * 针对特殊code做判断
     */
    private fun isFailure(response: Response): Boolean {
        if (ignoredFailureCode.contains(response.code()))
            return true
        val repMsg = JsonUtils.objectMapper.readValue(
            response.body()!!.byteStream(), OciErrorResponse::class.java
        )?.toJsonString()
        throw ArtifactPushException(
            "Response error for request ${response.request().url()}: " +
                "code is ${response.code()} and response is $repMsg"
        )
    }

    /**
     * 根据返回封装成功之后的请求数据
     */
    private fun wrapperSuccess(response: Response?): DefaultHandlerResult {
        return DefaultHandlerResult(
            isSuccess = true,
            isFailure = false,
            location = response?.header(HttpHeaders.LOCATION)
        )
    }

    /**
     * 根据返回封装失败之后的请求数据
     */
    private fun wrapperFailure(response: Response?): DefaultHandlerResult {
        return DefaultHandlerResult(
            isSuccess = false,
            isFailure = true,
            location = response?.header(HttpHeaders.LOCATION)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultHandler::class.java)
    }
}
