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
 * 上游边快照同步请求
 *
 * 推送方将本地"指向接收方"的全部 REPLICA_PUSH 边组装成一份快照，
 * 由接收方原子覆盖 (upstreamClusterName=推送方, sourceType=REPLICA_PUSH) 范围内的旧记录。
 *
 * FEDERATION 边由各成员节点本地直写维护（联邦创建/更新时已在每个成员节点写入完整副本），
 * 不在此快照范围内；entries 中混入非 REPLICA_PUSH 条目会被接收方拒绝。
 *
 * 即使本周期推送方对接收方已没有任何边，仍需推送 entries 为空的快照，
 * 以便接收方能彻底清理推送方残留的边（确保删除场景最终一致）。
 */
@Schema(title = "上游边快照同步请求")
data class UpstreamEdgeSyncRequest(
    @Schema(title = "推送方自身的 ClusterNode 名称")
    val upstreamClusterName: String,
    @Schema(title = "本快照中的全部条目；可以为空数组")
    val entries: List<UpstreamEdgeEntry>,
    @Schema(title = "快照生成时间戳")
    val snapshotTime: LocalDateTime = LocalDateTime.now()
)
