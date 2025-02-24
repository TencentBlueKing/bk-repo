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

package com.tencent.bkrepo.replication.pojo.task

import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "同步任务信息")
data class ReplicaTaskInfo(
    @get:Schema(title = "任务id，全局唯一")
    val id: String,
    @get:Schema(title = "任务key，全局唯一")
    val key: String,
    @get:Schema(title = "任务名称，允许重复")
    val name: String,
    @get:Schema(title = "所属项目")
    val projectId: String,
    @get:Schema(title = "同步对象类型", required = true)
    val replicaObjectType: ReplicaObjectType,
    @get:Schema(title = "同步类型")
    val replicaType: ReplicaType,
    @get:Schema(title = "任务设置")
    val setting: ReplicaSetting,
    @get:Schema(title = "远程集群集合")
    val remoteClusters: Set<ClusterNodeName>,
    @get:Schema(title = "任务描述")
    val description: String? = null,
    @get:Schema(title = "任务状态")
    val status: ReplicaStatus? = null,
    @get:Schema(title = "已同步字节数")
    var replicatedBytes: Long? = 0,
    @get:Schema(title = "总字节数")
    val totalBytes: Long? = 0,
    @get:Schema(title = "上次执行状态")
    var lastExecutionStatus: ExecutionStatus? = null,
    @get:Schema(title = "上次执行时间")
    var lastExecutionTime: LocalDateTime? = null,
    @get:Schema(title = "下次执行时间")
    var nextExecutionTime: LocalDateTime? = null,
    @get:Schema(title = "执行次数")
    var executionTimes: Long,
    @get:Schema(title = "是否启用")
    var enabled: Boolean = true,
    @get:Schema(title = "是否记录分发日志")
    val record: Boolean? = null,
    @get:Schema(title = "分发日志保留天数")
    val recordReserveDays: Long? = null,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "创建日期")
    val createdDate: String,
    @get:Schema(title = "上次修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "上次修改日期")
    val lastModifiedDate: String
)
