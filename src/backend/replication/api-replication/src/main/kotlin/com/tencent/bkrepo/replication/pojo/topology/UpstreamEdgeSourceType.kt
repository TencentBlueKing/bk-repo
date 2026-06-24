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

/**
 * 上游边的来源类型
 */
enum class UpstreamEdgeSourceType {
    /**
     * 推送类 ReplicaTask（SCHEDULED / REAL_TIME / RUN_ONCE）。
     * A→B 的关系数据原本住在 A，需要由 A 跨集群推送给 B。
     */
    REPLICA_PUSH,

    /**
     * EDGE_PULL ReplicaTask。
     * A→B 的关系数据本来就住在 B（拉取方本地），由 B 本地直写自己的 UpstreamEdge 表。
     */
    EDGE_PULL,

    /**
     * 联邦仓库（FederatedRepository）。
     * 联邦关系等价于双向 PUSH，跨集群推送 + 本地镜像直写双路径并存。
     */
    FEDERATION
}
