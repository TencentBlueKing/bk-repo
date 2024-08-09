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

package com.tencent.bkrepo.common.ratelimiter.rule.usage.user

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户上传用量限流配置规则实现
 */
open class UserUploadUsageRateLimitRule(
    // 用户+用量对应规则
    private val userUsageLimitRules: ConcurrentHashMap<String, UserUsageResourceLimitRule> = ConcurrentHashMap(),
    // 用户对应规则
    private val userLimitRules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()
) : RateLimitRule {


    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        var resLimitInfo = findConfigRule(resInfo.resource, resInfo.extraResource)
        if (resLimitInfo == null) {
            resLimitInfo = findConfigRule(resInfo.resource, resInfo.extraResource, true)
        }
        if (resLimitInfo == null && resInfo.extraResource.isNotEmpty()) {
            val res = resInfo.extraResource.last()
            val (user, _) = ResourcePathUtils.getUserAndPath(res)
            val userRule = userLimitRules[user]
            if (userRule != null) {
                resLimitInfo = ResLimitInfo(res, userRule)
            }
            if (resLimitInfo == null && userLimitRules.containsKey(StringPool.POUND)) {
                resLimitInfo = ResLimitInfo(res, userLimitRules[StringPool.POUND]!!)
            }
        }
        return resLimitInfo
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        val (userId, path) = ResourcePathUtils.getUserAndPath(resourceLimit.resource)
        if (path.isEmpty()) {
            userLimitRules[userId] = resourceLimit
        } else {
            val userUsageResourceLimitRule = userUsageLimitRules.getOrDefault(userId, UserUsageResourceLimitRule())
            userUsageResourceLimitRule.addUserUsageResourceLimit(resourceLimit)
            userUsageLimitRules.putIfAbsent(userId, userUsageResourceLimitRule)
        }
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach {
            try {
                addRateLimitRule(it)
            } catch (e: Exception) {
                logger.error("add config $it for ${this.javaClass.simpleName} failed, error is ${e.message}")
            }
        }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.USER_UPLOAD_USAGE.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    /**
     * 根据资源获取配置
     */
    private fun findConfigRule(
        resource: String, extraResource: List<String>, userPattern: Boolean = false
    ): ResLimitInfo? {
        var realResource = resource
        if (realResource.isBlank()) {
            return null
        }
        var (user, resWithoutUser) = ResourcePathUtils.getUserAndPath(realResource)
        if (userPattern) {
            user = StringPool.POUND
        }
        val userUsageRule = userUsageLimitRules[user]
        var ruleLimit = userUsageRule?.getPathResourceLimit(resWithoutUser)
        if (ruleLimit == null && extraResource.isNotEmpty()) {
            for (res in extraResource) {
                var (_, resWithoutUser) = ResourcePathUtils.getUserAndPath(res)
                ruleLimit = userUsageRule?.getPathResourceLimit(resWithoutUser)
                if (ruleLimit != null) {
                    realResource = res
                    break
                }
            }
        }
        if (ruleLimit == null) return null
        val resourceLimitCopy = ruleLimit.copy(resource = ResourcePathUtils.buildUserPath(user, ruleLimit.resource))
        return ResLimitInfo(realResource, resourceLimitCopy)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserUploadUsageRateLimitRule::class.java)
    }
}
