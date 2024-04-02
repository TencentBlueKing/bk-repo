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

package com.tencent.bkrepo.common.artifact.cache.pojo

import com.tencent.bkrepo.common.artifact.cache.model.TArtifactPreloadPlan
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("预加载执行计划")
data class ArtifactPreloadPlan(
    val id: String? = null,
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime,
    @ApiModelProperty("所属策略ID，仅用于记录执行计划来源")
    val strategyId: String? = null,
    @ApiModelProperty("所属项目ID，仅用于记录执行计划来源")
    val projectId: String? = null,
    @ApiModelProperty("所属仓库，仅用于记录执行计划来源")
    val repoName: String? = null,
    @ApiModelProperty("待加载制品的完整路径，仅用于记录执行计划来源")
    val fullPath: String? = null,
    @ApiModelProperty("待加载制品sha256")
    val sha256: String,
    @ApiModelProperty("待加载制品大小")
    val size: Long,
    @ApiModelProperty("待加载制品所在存储，为null时表示默认存储")
    val credentialsKey: String? = null,
    @ApiModelProperty("预加载计划执行毫秒时间戳")
    val executeTime: Long,
    @ApiModelProperty("预加载计划执行状态")
    val status: String = STATUS_PENDING
) {
    fun toPo() = TArtifactPreloadPlan(
        id = id,
        createdDate = createdDate,
        lastModifiedDate = lastModifiedDate,
        strategyId = strategyId,
        projectId = projectId,
        repoName = repoName,
        fullPath = fullPath,
        sha256 = sha256,
        size = size,
        credentialsKey = credentialsKey,
        executeTime = executeTime,
        status = status,
    )

    fun artifactInfo() = "plan[$id] credentials[$credentialsKey] sha256[$sha256] size[$size]"

    companion object {
        /**
         * 等待执行
         */
        const val STATUS_PENDING = "PENDING"

        /**
         * 执行中
         */
        const val STATUS_EXECUTING = "EXECUTING"
        fun TArtifactPreloadPlan.toDto() = ArtifactPreloadPlan(
            id = id,
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate,
            strategyId = strategyId,
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            sha256 = sha256,
            size = size,
            credentialsKey = credentialsKey,
            executeTime = executeTime,
            status = status
        )
    }
}
