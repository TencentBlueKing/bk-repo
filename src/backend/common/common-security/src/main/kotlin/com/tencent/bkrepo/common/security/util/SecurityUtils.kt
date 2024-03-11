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

package com.tencent.bkrepo.common.security.util

import com.tencent.bkrepo.common.api.constant.ADMIN_USER
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.AUTHORITIES_KEY
import com.tencent.bkrepo.common.api.constant.MS_REQUEST_KEY
import com.tencent.bkrepo.common.api.constant.MS_REQUEST_SRC_CLUSTER
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.metadata.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST
import org.springframework.web.context.request.RequestContextHolder

/**
 * SecurityUtils工具类
 */
object SecurityUtils {

    private val logger = LoggerFactory.getLogger(SecurityUtils::class.java)

    /**
     * 获取userId
     */
    fun getUserId(): String {
        return HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
    }

    /**
     * 是否系统管理员
     */
    fun isAdmin(): Boolean {
        return HttpContextHolder.getRequestOrNull()?.getAttribute(ADMIN_USER) as? Boolean ?: false
    }

    /**
     * 获取platform account id
     */
    fun getPlatformId(): String? {
        return HttpContextHolder.getRequestOrNull()?.getAttribute(PLATFORM_KEY) as? String
    }

    /**
     * 获取principle
     */
    fun getPrincipal(): String {
        return getPlatformId()?.let { "$it-${getUserId()}" } ?: getUserId()
    }

    /**
     * 获取authorities
     */
    fun getAuthorities(): Set<String> {
        val authorities = HttpContextHolder.getRequestOrNull()?.getAttribute(AUTHORITIES_KEY).toString()
        return if (authorities == "null") {
            emptySet()
        } else {
            authorities.split(StringPool.COMMA).toSet()
        }
    }

    /**
     * 判断是否为匿名用户
     */
    fun isAnonymous(): Boolean {
        return getUserId() == ANONYMOUS_USER
    }

    /**
     * 判断是否为微服务请求
     */
    fun isServiceRequest(): Boolean {
        try {
            return RequestContextHolder.getRequestAttributes()?.getAttribute(MS_REQUEST_KEY, SCOPE_REQUEST) != null
        } catch (e: IllegalStateException) {
            logger.error("check isServiceRequest failed", e)
        }
        return false
    }

    /**
     * 获取集群间请求来源集群名
     */
    fun getClusterName(): String? {
        return HeaderUtils.getHeader(MS_REQUEST_SRC_CLUSTER)
    }

    /**
     * 临时以系统用户身份执行
     */
    fun <R> sudo(action: () -> R?): R? {
        val userId = getUserId()
        HttpContextHolder.getRequestOrNull()?.setAttribute(USER_KEY, SYSTEM_USER)
        try {
            return action()
        } finally {
            HttpContextHolder.getRequestOrNull()?.setAttribute(USER_KEY, userId)
        }
    }
}
