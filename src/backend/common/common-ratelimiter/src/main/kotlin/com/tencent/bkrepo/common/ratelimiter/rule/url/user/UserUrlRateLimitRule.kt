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
import java.util.concurrent.ConcurrentHashMap

class UserUrlRateLimitRule: RateLimitRule {
    private val userLimitRules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()
    private val userUrlLimitRules: ConcurrentHashMap<String, UserUrlResourceLimitRule> = ConcurrentHashMap()
    private val userUrlTemplateLimitRules: ConcurrentHashMap<String, UserUrlResourceLimitRule> = ConcurrentHashMap()

    override fun getRateLimitRule(resource: String, extraResource: List<String>): ResourceLimit? {
        if (resource.isBlank()) return null
        var ruleLimit = userLimitRules[resource]
        if (ruleLimit == null && userLimitRules.containsKey(StringPool.POUND)) {
            ruleLimit = userLimitRules[StringPool.POUND]
        }

//        if (ruleLimit == null && extraResource.isNotEmpty()) {
//            ruleLimit = userUrlLimitRules[resource].getUrlResourceLimit()
//        }
        return ruleLimit
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        if (resourceLimit.resource.isBlank()) {
            return
        }
        when (resourceLimit.limitDimension) {
            LimitDimension.USER -> userLimitRules[resourceLimit.resource] = resourceLimit
//            LimitDimension.USER_URL -> {
//                userUrlLimitRules[res]
//            }
//            LimitDimension.USER_URL_TEMPLATE -> urlTemplateLimitRules.addUrlResourceLimit(resourceLimit)
            else -> return
        }
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach {
            addRateLimitRule(it)
        }
    }
}