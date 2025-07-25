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
    // 只对指定url进行从request body解析项目仓库信息
    var specialUrls: List<String> = emptyList(),
    var bandwidthProperties: BandwidthProperties = BandwidthProperties(),
    // 是否开启项目访问白名单
    var projectWhiteListEnabled: Boolean = false,
    // 项目访问白名单。 当开启白名单访问后，只有在白名单列表上的项目才允许访问
    var projectWhiteList: List<String> = emptyList(),
    // 针对开启访问白名单的场景下，部分url没有办法获取到项目信息时，直接放通
    var specialUrlsIgnoreProjectWhiteList: List<String> = emptyList()
)
