/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.pojo.topology

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 上游边信息（运维分页查询返回）
 */
@Schema(title = "上游边信息")
data class UpstreamEdgeInfo(
    @Schema(title = "记录 id")
    val id: String?,
    @Schema(title = "上游集群名称")
    val upstreamClusterName: String,
    @Schema(title = "上游 ReplicaTask / FederatedRepository key")
    val replicaTaskKey: String,
    @Schema(title = "上游 ReplicaTask / FederatedRepository 名称")
    val replicaTaskName: String?,
    @Schema(title = "边来源类型")
    val sourceType: UpstreamEdgeSourceType,
    @Schema(title = "边状态")
    val status: UpstreamEdgeStatus,
    @Schema(title = "最近一次被覆盖时间")
    val lastSyncTime: LocalDateTime,
    @Schema(title = "创建时间")
    val createdDate: LocalDateTime,
    @Schema(title = "最近修改时间")
    val lastModifiedDate: LocalDateTime
)
