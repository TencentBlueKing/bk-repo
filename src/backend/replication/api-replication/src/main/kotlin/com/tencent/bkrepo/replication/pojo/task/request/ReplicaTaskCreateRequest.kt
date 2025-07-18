/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.pojo.task.request

import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "同步任务创建请求")
data class ReplicaTaskCreateRequest(
    @get:Schema(title = "任务名称", required = true)
    val name: String,
    @get:Schema(title = "本地项目", required = true)
    val localProjectId: String,
    @get:Schema(title = "同步对象类型", required = true)
    val replicaObjectType: ReplicaObjectType,
    @get:Schema(title = "任务对象信息", required = true)
    val replicaTaskObjects: List<ReplicaObjectInfo>,
    @get:Schema(title = "同步类型", required = true)
    val replicaType: ReplicaType = ReplicaType.SCHEDULED,
    @get:Schema(title = "任务设置", required = true)
    val setting: ReplicaSetting,
    @get:Schema(title = "远程集群集合", required = true)
    val remoteClusterIds: Set<String>,
    @get:Schema(title = "是否启用", required = true)
    val enabled: Boolean = true,
    @get:Schema(title = "任务描述", required = false)
    val description: String? = null,
    @get:Schema(title = "是否记录分发日志", required = true)
    val record: Boolean = true,
    @get:Schema(title = "分发日志保留天数", required = true)
    val recordReserveDays: Long = 30,
)
