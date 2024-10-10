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

package com.tencent.bkrepo.common.ratelimiter.rule.url.user

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.PathResourceLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.PathNode
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils.getUserAndPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 用户+url对应规则
 * 如果配置了父路径，则该用户对应访问的子路径也会遵循父路径对应的配置规则，即每个子路径单独拥有对应父路径所配置的限流规则；
 * 如果配置指定路径，则只有该用户访问的指定路径遵循对应的配置规则，即该指定路径独享所配置的限流规则。
 */
class UserUrlResourceLimitRule(
    root: PathNode = PathNode("/"),
    private var user: String = StringPool.POUND
) : PathResourceLimitRule(root) {

    fun addUserUrlResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension !in userLimitDimensionList) {
            return
        }
        val (userId, urlPath) = getUserAndPath(resourceLimit.resource)
        if (!urlPath.startsWith("/") || user.isBlank()) {
            logger.warn("$resourceLimit is invalid")
            throw InvalidResourceException(urlPath)
        }
        user = userId
        val resourceLimitCopy = resourceLimit.copy(resource = urlPath)
        addPathResourceLimit(resourceLimitCopy, userLimitDimensionList)
    }

    fun addUserUrlResourceLimits(resourceLimits: List<ResourceLimit>) {
        resourceLimits.forEach {
            addUserUrlResourceLimit(it)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserUrlResourceLimitRule::class.java)
        private val userLimitDimensionList = listOf(LimitDimension.USER_URL.name)
    }
}
