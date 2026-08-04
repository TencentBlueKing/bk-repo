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

package com.tencent.bkrepo.opdata.cluster.topology.model

import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 集群节点展示性元数据扩展集合。
 *
 * 该集合独立于 replication 模块的 cluster_node 集合维护，专门用于
 * 拓扑图渲染时补充地域、网络区域、显示名称、描述等管理性字段，
 * 避免污染核心 cluster_node 数据。
 */
@Document("cluster_node_extension")
data class TClusterNodeExtension(
    var id: String? = null,

    /**
     * 关联的集群节点名称，与 replication 模块 cluster_node.name 对应。
     */
    @Indexed(unique = true)
    var clusterName: String,

    /**
     * 地域标识，例如 sz / sh / hk / sg / na 等。
     */
    var region: String? = null,

    /**
     * 网络区域，例如 IDC内网 / 外网 / devnet / 云研发内网。
     */
    var networkZone: String? = null,

    /**
     * 拓扑图上显示用的友好名称，未配置时回退使用 clusterName。
     */
    var displayName: String? = null,

    /**
     * 描述备注。
     */
    var description: String? = null,

    /**
     * 最近一次修改人。
     */
    var lastModifiedBy: String,

    /**
     * 最近一次修改时间。
     */
    var lastModifiedDate: LocalDateTime
)
