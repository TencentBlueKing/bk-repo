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

package com.tencent.bkrepo.analyst.configuration

import com.tencent.bkrepo.analyst.distribution.DistributedCountFactory.Companion.DISTRIBUTED_COUNT_REDIS
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("scanner")
data class ScannerProperties(
    /**
     * 默认项目扫描子任务数量限制
     */
    var defaultProjectSubScanTaskCountLimit: Int = DEFAULT_SUB_SCAN_TASK_COUNT_LIMIT,
    /**
     * 扫描报告地址
     */
    var detailReportUrl: String = "http://localhost",
    /**
     * 后端服务baseUrl
     */
    var baseUrl: String = "http://localhost",
    /**
     * 前端baseUrl
     */
    var frontEndBaseUrl: String = "http://localhost/ui",
    /**
     * 用于监控数据统计的分布式计数器使用的存储类型
     */
    var distributedCountType: String = DISTRIBUTED_COUNT_REDIS,
    /**
     * 默认分发器
     */
    var defaultDispatcher: String = ""
) {
    companion object {
        const val DEFAULT_PROJECT_SCAN_PRIORITY = 0
        const val DEFAULT_SCAN_TASK_COUNT_LIMIT = 1
        const val DEFAULT_SUB_SCAN_TASK_COUNT_LIMIT = 20
    }
}
