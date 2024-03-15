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
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.UrlUtils
import java.util.concurrent.ConcurrentHashMap

class UserUrlRateLimitRule: RateLimitRule {
    private val userLimitRules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()
    private val userUrlLimitRules: ConcurrentHashMap<String, UserUrlResourceLimitRule> = ConcurrentHashMap()
    private val userUrlTemplateLimitRules: ConcurrentHashMap<String, UserUrlResourceLimitRule> = ConcurrentHashMap()

    override fun getRateLimitRule(resource: String, extraResource: List<String>): ResourceLimit? {
        if (resource.isBlank()) return null
        val (userId, url) = UrlUtils.getUserAndUrl(resource)
        var ruleLimit = userUrlLimitRules[userId]?.getUrlResourceLimit(url)
        if (ruleLimit == null && userUrlLimitRules.containsKey(StringPool.POUND)) {
            ruleLimit = userUrlLimitRules[StringPool.POUND]?.getUrlResourceLimit(url)
        }
        if (ruleLimit == null) {
            ruleLimit = userUrlTemplateLimitRules[userId]?.getUrlResourceLimit(url)
            if (ruleLimit == null && userUrlTemplateLimitRules.containsKey(StringPool.POUND)) {
                ruleLimit = userUrlTemplateLimitRules[StringPool.POUND]?.getUrlResourceLimit(url)
            }
            if (ruleLimit == null) {
                ruleLimit = userLimitRules[userId]
                if (ruleLimit == null && userLimitRules.containsKey(StringPool.POUND)) {
                    ruleLimit = userLimitRules[StringPool.POUND]
                }
            }
        }
        if (ruleLimit == null && extraResource.isNotEmpty()) {
            val (userId, urlMapping) = UrlUtils.getUserAndUrl(extraResource.first())
            ruleLimit = userUrlTemplateLimitRules[userId]?.getUrlResourceLimit(urlMapping)
            if (ruleLimit == null && userUrlTemplateLimitRules.containsKey(StringPool.POUND)) {
                ruleLimit = userUrlTemplateLimitRules[StringPool.POUND]?.getUrlResourceLimit(urlMapping)
            }
        }
        return ruleLimit
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        if (resourceLimit.resource.isBlank()) {
            return
        }
        when (resourceLimit.limitDimension) {
            LimitDimension.USER -> userLimitRules[resourceLimit.resource] = resourceLimit
            LimitDimension.USER_URL -> {
                val (userId, _) = UrlUtils.getUserAndUrl(resourceLimit.resource)
                val userUrlResourceLimitRule = userUrlLimitRules[userId] ?: UserUrlResourceLimitRule()
                userUrlResourceLimitRule.addUrlResourceLimit(resourceLimit)
                userUrlLimitRules.putIfAbsent(userId, userUrlResourceLimitRule)
            }
            LimitDimension.USER_URL_TEMPLATE -> {
                val (userId, _) = UrlUtils.getUserAndUrl(resourceLimit.resource)
                val userUrlResourceLimitRule = userUrlTemplateLimitRules[userId] ?: UserUrlResourceLimitRule()
                userUrlResourceLimitRule.addUrlResourceLimit(resourceLimit)
                userUrlTemplateLimitRules.putIfAbsent(userId, userUrlResourceLimitRule)
            }
            else -> return
        }
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach {
            addRateLimitRule(it)
        }
    }
}