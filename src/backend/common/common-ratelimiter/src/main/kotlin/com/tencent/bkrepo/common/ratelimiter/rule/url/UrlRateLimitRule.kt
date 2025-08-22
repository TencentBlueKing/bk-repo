/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.ratelimiter.rule.url

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * URL限流配置规则实现
 */
class UrlRateLimitRule : RateLimitRule {

    // 指定URL规则
    private val urlLimitRules: UrlResourceLimitRule = UrlResourceLimitRule()

    override fun isEmpty(): Boolean {
        return urlLimitRules.isEmpty()
    }

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        var realResource = resInfo.resource
        if (realResource.isBlank()) {
            return null
        }
        var ruleLimit = urlLimitRules.getPathResourceLimit(realResource)
        if (ruleLimit == null && resInfo.extraResource.isNotEmpty()) {
            for (res in resInfo.extraResource) {
                ruleLimit = urlLimitRules.getPathResourceLimit(res)
                if (ruleLimit != null) {
                    realResource = res
                    break
                }
            }
        }
        if (ruleLimit == null) return null
        return ResLimitInfo(realResource, ruleLimit)
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        urlLimitRules.addUrlResourceLimit(resourceLimit)
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
        if (resourceLimit.limitDimension != LimitDimension.URL.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UrlRateLimitRule::class.java)
    }
}
