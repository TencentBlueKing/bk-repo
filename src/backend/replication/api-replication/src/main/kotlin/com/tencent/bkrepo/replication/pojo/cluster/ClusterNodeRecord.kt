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

package com.tencent.bkrepo.replication.pojo.cluster

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import java.time.LocalDateTime

/**
 * `cluster_node` 集合的只读投影 pojo（DB 视图）。
 *
 * 区别于 [ClusterNodeInfo]（对外 API 强类型 DTO，带 Swagger schema 与非空约束），
 * 本类用于跨模块只读访问 MongoDB 集合：
 * - 不带 @Document，不参与 replication 模块自身持久化映射；
 * - 状态/检测类型等枚举字段使用 String 弱绑定，避免外部模块依赖 replication 内部枚举；
 * - 字段全部允许为 null，以兼容历史数据可能缺字段的情况。
 *
 * 当前使用方：opdata 拓扑/REMOTE 节点统计。
 *
 * @see com.tencent.bkrepo.replication.model.TClusterNode replication 模块内部权威实体
 */
data class ClusterNodeRecord(
    var id: String? = null,
    /** 集群名称，集合中唯一 */
    var name: String,
    /** 集群类型：CENTER / EDGE / STANDALONE / REMOTE */
    var type: ClusterNodeType,
    /** 集群访问地址 */
    var url: String? = null,
    /** 集群健康状态：HEALTHY / UNHEALTHY 等，使用 String 避免枚举耦合 */
    var status: String? = null,
    /** 状态为非健康时的失败原因 */
    var errorReason: String? = null,
    /** EDGE 连通性检测方式：REPORT / PING 等 */
    var detectType: String? = null,
    /** 最近上报心跳时间 */
    var lastReportTime: LocalDateTime? = null
) {
    companion object {
        /** 对应 MongoDB 集合名 */
        const val COLLECTION_NAME = "cluster_node"
    }
}
