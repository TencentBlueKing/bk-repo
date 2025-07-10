/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

class UserUrlRepoResourceLimitRule(
    root: PathNode = PathNode("/"),
    pathLengthCheck: Boolean = true,
    private var user: String = StringPool.POUND,
) : PathResourceLimitRule(root, pathLengthCheck) {

    fun addUserUrlRepoResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension !in userUrlRepoDimensionList) {
            return
        }
        val (userId, urlPath) = getUserAndPath(resourceLimit.resource)
        if (!urlPath.startsWith("/") || user.isBlank()) {
            logger.warn("$resourceLimit is invalid")
            throw InvalidResourceException(urlPath)
        }
        user = userId
        val resourceLimitCopy = resourceLimit.copy(resource = urlPath)
        addPathResourceLimit(resourceLimitCopy, userUrlRepoDimensionList)
    }

    fun addUserUrlRepoResourceLimits(resourceLimits: List<ResourceLimit>) {
        resourceLimits.forEach {
            addUserUrlRepoResourceLimit(it)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserUrlRepoResourceLimitRule::class.java)
        private val userUrlRepoDimensionList = listOf(
            LimitDimension.USER_URL_REPO.name
        )
    }
}