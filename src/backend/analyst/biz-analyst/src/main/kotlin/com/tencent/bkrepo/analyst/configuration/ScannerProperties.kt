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
import java.time.Duration

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
    var defaultDispatcher: String = "",
    /**
     * 结果报告数据导出配置
     */
    var reportExport: ReportExportProperties? = null,

    /**
     * 阻塞超时时间，项目提交的分析任务数量超过配额后继续提交的任务会进入阻塞状态，阻塞超过这个时间将会阻塞超时导致任务失败
     * 为0时表示任务将不会因为阻塞而超时
     */
    var blockTimeout: Duration = Duration.ofSeconds(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS),
    /**
     * 任务心跳超时时间，当任务超过这个时间未上报状态时将会触发超时, 0表示不检查任务心跳
     */
    var heartbeatTimeout: Duration = Duration.ofMinutes(0),
    /**
     * 任务最长执行时间，超过后将不再重试而是直接转为超时状态
     */
    var maxTaskDuration: Duration = Duration.ofSeconds(EXPIRED_SECONDS),
    /**
     * 生成的制品临时下载链接超时时间
     */
    var tempDownloadUrlExpireDuration: Duration = Duration.ofSeconds(30),
    /**
     * 生成的制品临时下载链接超时时间允许下载的次数
     */
    var tempDownloadUrlPermits: Int? = null,
    /**
     * 最大全局扫描任务数量
     */
    var maxGlobalTaskCount: Int = 1,
) {
    companion object {
        /**
         * 默认任务最长执行时间，超过后会触发重试
         */
        const val DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS = 1200L
        /**
         * 任务过期时间
         */
        const val EXPIRED_SECONDS = 24 * 60 * 60L
        const val DEFAULT_PROJECT_SCAN_PRIORITY = 0
        const val DEFAULT_SCAN_TASK_COUNT_LIMIT = 1
        const val DEFAULT_SUB_SCAN_TASK_COUNT_LIMIT = 20
    }
}
