/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.external.rest.base

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.replication.constant.TOKEN
import com.tencent.bkrepo.replication.replica.external.exception.RepoDeployException
import com.tencent.bkrepo.replication.util.HttpUtils
import okhttp3.OkHttpClient
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * 抽象请求处理类
 */
open class Handler(
    private val httpClient: OkHttpClient
) {
    var successHandler: Handler? = null
    var failHandler: Handler? = null
    // 请求各种参数属性
    var map: MutableMap<String, Any?> = mutableMapOf()

    /**
     * 请求前置处理：
     * 现主要是auth校验
     */
    open fun processBefore(beforeMap: Map<String, Any>? = null): String? {
        if (beforeMap?.get(TOKEN) != null) return beforeMap[TOKEN] as String
        if (this.map[TOKEN] != null) return this.map[TOKEN] as String
        return null
    }

    /**
     * 对传入请求进行处理判断
     */
    fun process(map: Map<String, Any>? = null): Boolean {
        val token = this.processBefore(map)
        val request = HttpUtils.wrapperRequest(token = token, originalMap = this.map, extraMap = map)
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
                    val extraMap = wrapperSuccessMap(it)
                    this.successHandler?.process(extraMap) ?: true
                }
                isFailure(it) -> {
                    logger.info("Result of the request ${it.request().url()} is failure")
                    val extraMap = wrapperFailureMap(it)
                    this.failHandler?.process(extraMap) ?: true
                }
                else -> throw RepoDeployException("invalid response  ${it.code()} for request ${it.request().url()}")
            }
        }
    }

    /**
     * 判断请求是否成功
     */
    open fun isSuccess(response: Response): Boolean {
        return response.isSuccessful
    }

    /**
     * 针对特殊code做判断
     */
    open fun isFailure(response: Response): Boolean {
        return HttpStatus.NOT_FOUND.value == response.code()
    }

    /**
     * 根据返回封装成功之后的请求数据
     */
    open fun wrapperSuccessMap(response: Response?): Map<String, Any>? {
        return null
    }

    /**
     * 根据返回封装失败之后的请求数据
     */
    open fun wrapperFailureMap(response: Response?): Map<String, Any>? {
        return null
    }

    /**
     * 设置请求相关属性
     */
    open fun setRequestProperty(map: Map<String, Any?>) {
        this.map.putAll(map)
    }

    /**
     * 设置Handler
     */
    fun setHandler(successHandler: Handler? = null, failHandler: Handler? = null) {
        this.successHandler = successHandler
        this.failHandler = failHandler
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Handler::class.java)
    }
}
