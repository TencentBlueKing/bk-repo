/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.config.properties

import org.springframework.scheduling.annotation.Scheduled

open class BatchJobProperties(
    /**
     * 是否开启任务
     * */
    open var enabled: Boolean = true,
    /**
     * 任务亲和节点列表，未配置时将可以调度到任意节点，配置后只能调度到亲和的节点
     */
    open var affinityNodeIps: Set<String> = emptySet(),

    /**
     * cron表达式
     * */
    open var cron: String = Scheduled.CRON_DISABLED,
    open var fixedDelay: Long = 0,
    open var fixedRate: Long = 0,
    open var initialDelay: Long = 0,

    /**
     * 停止任务超时时间，查过该时间，则会强制停止任务
     * */
    var stopTimeout: Long = 30000,

    /**
     * 任务分布式锁名
     */
    var lockName: String? = null,

    // ----v2 job----
    /**
     * 是否在新调度框架下开启任务
     * */
    open var enabledV2: Boolean = false,

    /**
     * 工作组名称，为空则使用默认工作组
     * */
    open var workerGroup: String = "",

    /**
     * 任务是否动态分片
     * */
    open var sharding: Boolean = false,
)
