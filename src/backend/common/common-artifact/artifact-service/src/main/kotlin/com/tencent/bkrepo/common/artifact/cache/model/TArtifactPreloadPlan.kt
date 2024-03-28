/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.cache.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 预加载执行计划
 */
@Document("artifact_preload_plan")
@CompoundIndexes(
    CompoundIndex(name = "status_executeTime_idx", def = "{'status': 1, 'executeTime': 1}", background = true),
    CompoundIndex(name = "projectId_repoName_idx", def = "{'projectId': 1, 'repoName': 1}", background = true)
)
data class TArtifactPreloadPlan(
    val id: String? = null,
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime,
    /**
     * 所属策略ID，仅用于记录执行计划来源
     */
    val strategyId: String? = null,
    /**
     * 所属项目ID，仅用于记录执行计划来源
     */
    val projectId: String? = null,
    /**
     * 所属仓库，仅用于记录执行计划来源
     */
    val repoName: String? = null,
    /**
     * 待加载制品的完整路径，仅用于记录执行计划来源
     */
    val fullPath: String? = null,
    /**
     * 待加载制品sha256
     */
    val sha256: String,
    /**
     * 待加载制品大小
     */
    val size: Long,
    /**
     * 待加载制品所在存储，为null时表示默认存储
     */
    val credentialsKey: String? = null,
    /**
     * 预加载计划执行毫秒时间戳
     */
    val executeTime: Long,
    /**
     * 计划执行状态
     */
    val status: String,
)
