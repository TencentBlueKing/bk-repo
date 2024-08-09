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

package com.tencent.bkrepo.common.ratelimiter.config

import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rate.limiter")
data class RateLimiterProperties(
    var enabled: Boolean = false,
    var dryRun: Boolean = false,
    // 配置规则刷新频率 单位为秒
    var refreshDuration: Long = 10L,
    // 本地缓存限流算法实现的最大个数
    var cacheCapacity: Long = 1024L,
    // 限流配置
    var rules: List<ResourceLimit> = mutableListOf(),
    // 等待时间，单位毫秒
    var sleepTime: Long = 10,
    // 重试次数
    var retryNum: Int = 10,
    // 针对读流的请求，避免频繁去请求，每次申请固定大小
    var permitsNum: Long = 1024 * 1024 * 1024
)
