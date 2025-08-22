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

package com.tencent.bkrepo.common.ratelimiter.rule

import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit

/**
 * 限流配置规则处理
 */
interface RateLimitRule {

    /**
     * 是否存在相应配置规则
     */
    fun isEmpty(): Boolean

    /**
     * 获取资源对应的规则
     * 优先查找resource， 如查不到对应规则，则通过extraResource查找
     * resource一般是特定类型，如特定用户，特定URL，特定项目仓库等
     * extraResource一般是某一类类型，如所有用户、URL模版、所有仓库等
     */
    fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo?

    /**
     * 添加限流规则
     */
    fun addRateLimitRule(resourceLimit: ResourceLimit)

    /**
     * 批量添加限流规则
     */
    fun addRateLimitRules(resourceLimit: List<ResourceLimit>)

    /**
     * 过滤不符合条件的规则
     */
    fun filterResourceLimit(resourceLimit: ResourceLimit)
}
