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

package com.tencent.bkrepo.common.ratelimiter.rule.url

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.ResourceLimit

class UrlRateLimitRule: RateLimitRule {

    @Volatile
    private var urlLimitRules: UrlResourceLimitRule = UrlResourceLimitRule()
    @Volatile
    private var urlTemplateLimitRules: UrlResourceLimitRule = UrlResourceLimitRule()

    override fun getRateLimitRule(resource: String, extraResource: List<String>): ResourceLimit? {
        if (resource.isBlank()) return null
        var ruleLimit = urlLimitRules.getUrlResourceLimit(resource)
        if (ruleLimit == null && extraResource.isNotEmpty()) {
            ruleLimit = urlTemplateLimitRules.getUrlResourceLimit(resource)
        }
        return ruleLimit
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        if (resourceLimit.resource.isBlank()) {
            return
        }
        when (resourceLimit.limitDimension) {
            LimitDimension.URL -> urlLimitRules.addUrlResourceLimit(resourceLimit)
            LimitDimension.URL_TEMPLATE -> urlTemplateLimitRules.addUrlResourceLimit(resourceLimit)
            else -> return
        }
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach {
            addRateLimitRule(it)
        }
    }
}
