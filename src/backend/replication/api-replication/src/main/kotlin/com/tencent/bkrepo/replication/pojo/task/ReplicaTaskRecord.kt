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

package com.tencent.bkrepo.replication.pojo.task

import java.time.LocalDateTime

/**
 * `replica_task` 集合的只读投影 pojo（DB 视图）。
 *
 * 区别于 [ReplicaTaskInfo]（对外 API 强类型 DTO，带 Swagger schema 与非空约束），
 * 本类用于跨模块只读访问 MongoDB 集合：
 * - 不带 @Document，不参与 replication 模块自身持久化映射；
 * - 枚举字段（replicaType / status / lastExecutionStatus）使用 String 弱绑定，
 *   避免外部模块依赖 replication 内部枚举（ReplicaType / ReplicaStatus / ExecutionStatus）；
 * - 字段全部允许为 null，以兼容历史数据可能缺字段的情况。
 *
 * 当前使用方：opdata 拓扑/REMOTE 节点统计。
 *
 * @see com.tencent.bkrepo.replication.model.TReplicaTask replication 模块内部权威实体
 */
data class ReplicaTaskRecord(
    var id: String? = null,
    /** 任务唯一 key */
    var key: String,
    /** 任务名称 */
    var name: String? = null,
    /** 项目 id */
    var projectId: String? = null,
    /** 同步类型：SCHEDULED / REAL_TIME / EDGE_PULL / FEDERATION 等 */
    var replicaType: String? = null,
    /** 远程集群集合，每个元素至少包含 name 字段 */
    var remoteClusters: List<RemoteClusterRef> = emptyList(),
    /** 是否启用 */
    var enabled: Boolean = true,
    /** 任务运行状态 */
    var status: String? = null,
    /** 上次执行状态 */
    var lastExecutionStatus: String? = null,
    /** 上次执行时间 */
    var lastExecutionTime: LocalDateTime? = null,
    /** 创建时间 */
    var createdDate: LocalDateTime? = null,
    /** 最近修改时间 */
    var lastModifiedDate: LocalDateTime? = null
) {
    /**
     * 远程集群引用，对应 replication 模块的 ClusterNodeName。
     */
    data class RemoteClusterRef(
        var id: String? = null,
        var name: String? = null
    )

    companion object {
        /** 对应 MongoDB 集合名 */
        const val COLLECTION_NAME = "replica_task"
    }
}
