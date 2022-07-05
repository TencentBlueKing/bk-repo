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

package com.tencent.bkrepo.replication.replica.thirdparty.rest.base

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.replication.pojo.thirdparty.RequestProperty
import com.tencent.bkrepo.replication.replica.thirdparty.exception.ArtifactPushException
import com.tencent.bkrepo.replication.util.HttpUtils
import okhttp3.OkHttpClient
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * 默认请求处理类
 */
class DefaultHandler(
    private val httpClient: OkHttpClient
) {
    private var successHandler: DefaultHandler? = null
    private var failHandler: DefaultHandler? = null
    // 请求各种参数属性
    lateinit var requestProperty: RequestProperty

    /**
     * 请求前置处理：
     */
    fun processBefore(property: RequestProperty? = null) {
        property?.authorizationCode?.let { requestProperty.authorizationCode = property.authorizationCode }
        property?.requestUrl?.let { requestProperty.requestUrl = property.requestUrl }
    }

    /**
     * 对传入请求进行处理判断
     */
    fun process(property: RequestProperty? = null): Boolean {
        this.processBefore(property)
        val request = HttpUtils.wrapperRequest(requestProperty)
        logger.info(
            "The url of the request is ${request.url()} and method is ${request.method()} " +
                "and current handler is ${this.javaClass.name}"
        )
        // TODO 可以考虑增加重试功能，但是需要限定特定场景
        val response = httpClient.newCall(request).execute()
        response.use {
            return when {
                isSuccess(it) -> {
                    logger.info("Result of the request ${it.request().url()} is successful")
                    val extraProperty = wrapperSuccess(it)
                    this.successHandler?.process(extraProperty) ?: true
                }
                isFailure(it) -> {
                    logger.info("Result of the request ${it.request().url()} is failure")
                    val extraProperty = wrapperFailure(it)
                    this.failHandler?.process(extraProperty) ?: true
                }
                else -> throw ArtifactPushException("invalid response  ${it.code()} for request ${it.request().url()}")
            }
        }
    }

    /**
     * 判断请求是否成功
     */
    private fun isSuccess(response: Response): Boolean {
        return response.isSuccessful
    }

    /**
     * 针对特殊code做判断
     */
    private fun isFailure(response: Response): Boolean {
        if (BAD_RESPONSE_CODE.contains(response.code())) {
            throw ArtifactPushException("Response error: code is ${response.code()}")
        }
        return true
    }

    /**
     * 根据返回封装成功之后的请求数据
     */
    private fun wrapperSuccess(response: Response?): RequestProperty? {
        return response?.let {
            val location = response.header(HttpHeaders.LOCATION)
            RequestProperty(
                requestUrl = location
            )
        }
    }

    /**
     * 根据返回封装失败之后的请求数据
     */
    private fun wrapperFailure(response: Response?): RequestProperty? {
        return null
    }

    /**
     * 设置Handler
     */
    fun setHandler(successHandler: DefaultHandler? = null, failHandler: DefaultHandler? = null) {
        this.successHandler = successHandler
        this.failHandler = failHandler
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultHandler::class.java)
        private val BAD_RESPONSE_CODE = mutableListOf(
            HttpStatus.BAD_REQUEST.value,
            HttpStatus.UNAUTHORIZED.value,
            HttpStatus.METHOD_NOT_ALLOWED.value,
            HttpStatus.FORBIDDEN.value,
            HttpStatus.CONFLICT.value
        )
    }
}
