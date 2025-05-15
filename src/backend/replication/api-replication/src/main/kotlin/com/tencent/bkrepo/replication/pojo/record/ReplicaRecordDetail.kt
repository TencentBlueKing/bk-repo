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

package com.tencent.bkrepo.replication.pojo.record

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "同步任务执行记录详情")
data class ReplicaRecordDetail(
    @get:Schema(title = "详情id")
    var id: String,
    @get:Schema(title = "记录id")
    var recordId: String,
    @get:Schema(title = "本地集群名称")
    var localCluster: String,
    @get:Schema(title = "远程集群名称")
    var remoteCluster: String,
    @get:Schema(title = "本地仓库名称")
    var localRepoName: String,
    @get:Schema(title = "仓库类型")
    val repoType: RepositoryType,
    @get:Schema(title = "包限制")
    var packageConstraint: PackageConstraint? = null,
    @get:Schema(title = "路径名称")
    var pathConstraint: PathConstraint? = null,
    @get:Schema(title = "制品名称")
    var artifactName: String? = null,
    @get:Schema(title = "包版本")
    var version: String? = null,
    @get:Schema(title = "冲突策略")
    var conflictStrategy: ConflictStrategy? = null,
    @get:Schema(title = "制品大小")
    var size: Long? = null,
    @get:Schema(title = "制品sha256")
    var sha256: String? = null,
    @get:Schema(title = "运行状态")
    var status: ExecutionStatus,
    @get:Schema(title = "同步进度")
    var progress: ReplicaProgress,
    @get:Schema(title = "开始时间")
    var startTime: LocalDateTime,
    @get:Schema(title = "结束时间")
    var endTime: LocalDateTime? = null,
    @get:Schema(title = "错误原因，未执行或执行成功则为null")
    var errorReason: String? = null
)
