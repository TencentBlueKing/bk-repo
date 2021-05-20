/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.pojo.task

import com.tencent.bkrepo.replication.pojo.common.PackageConstraint
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.request.TaskType
import com.tencent.bkrepo.replication.pojo.setting.ReplicaSetting
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("同步任务创建请求")
data class ReplicaTaskCreateRequest(
    @ApiModelProperty("任务名称", required = true)
    val name: String,
    @ApiModelProperty("本地项目", required = true)
    val localProjectId: String,
    @ApiModelProperty("本地仓库", required = true)
    val localRepoName: String,
    @ApiModelProperty("远程仓库, 为空则保持和localProjectId一致", required = false)
    val remoteProjectId: String? = null,
    @ApiModelProperty("远程仓库, 为空则保持和localRepoName一致", required = false)
    val remoteRepoName: String? = null,
    @ApiModelProperty("任务类型", required = true)
    val taskType: TaskType = TaskType.FULL,
    @ApiModelProperty("同步类型", required = true)
    val replicaType: ReplicaType = ReplicaType.FULL,
    @ApiModelProperty("任务设置", required = true)
    val setting: ReplicaSetting,
    @ApiModelProperty("任务是否启用", required = true)
    val enabled: Boolean = true,
    @ApiModelProperty("远程集群名称列表", required = true)
    val remoteClusterSet: Set<String>,
    @ApiModelProperty("包限制条件", required = false)
    val packageConstraints: Set<PackageConstraint>? = null,
    @ApiModelProperty("路径限制条件，包限制点路径限制都为空则同步整个仓库数据", required = false)
    val pathConstraints: Set<String>? = null,
    @ApiModelProperty("描述", required = false)
    val description: String? = null
)
