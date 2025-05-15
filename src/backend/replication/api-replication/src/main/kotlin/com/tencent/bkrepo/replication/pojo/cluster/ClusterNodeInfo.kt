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

package com.tencent.bkrepo.replication.pojo.cluster

import com.fasterxml.jackson.annotation.JsonIgnore
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 集群节点信息
 */
@Schema(title = "集群节点信息")
data class ClusterNodeInfo(
    @get:Schema(title = "集群id")
    val id: String?,
    @get:Schema(title = "集群名称")
    val name: String,
    @get:Schema(title = "集群节点类型")
    val status: ClusterNodeStatus,
    @get:Schema(title = "集群状态为非健康时显示失败原因")
    val errorReason: String?,
    @get:Schema(title = "集群节点类型")
    val type: ClusterNodeType,
    @get:Schema(title = "集群地址")
    val url: String,
    @get:Schema(title = "集群访问用户名，独立集群需要此字段")
    val username: String?,
    @get:Schema(title = "集群访问密码，独立集群需要此字段")
    @JsonIgnore
    val password: String?,
    @get:Schema(title = "集群的证书，独立集群需要此字段")
    val certificate: String?,
    @get:Schema(title = "集群appId", required = false)
    var appId: String? = null,
    @get:Schema(title = "集群访问凭证", required = false)
    var accessKey: String? = null,
    @get:Schema(title = "集群密钥", required = false)
    @JsonIgnore
    var secretKey: String? = null,
    @get:Schema(title = "udp端口", required = false)
    var udpPort: Int? = null,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "集群额外信息")
    var extension: Map<String, Any>? = null,
    @get:Schema(title = "创建日期")
    val createdDate: String,
    @get:Schema(title = "上次修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "上次修改日期")
    val lastModifiedDate: String,
    @get:Schema(title = "节点连通性检测方式")
    val detectType: DetectType?,
    @get:Schema(title = "最近上报心跳时间")
    val lastReportTime: LocalDateTime?
)
