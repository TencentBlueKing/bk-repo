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

package com.tencent.bkrepo.replication.pojo.cluster.request

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import io.swagger.v3.oas.annotations.media.Schema


/**
 * 添加集群节点请求
 */
@Schema(title = "添加集群节点请求")
class ClusterNodeCreateRequest(
    @get:Schema(title = "添加的集群名称", required = true)
    var name: String,
    @get:Schema(title = "集群地址", required = true)
    var url: String,
    @get:Schema(title = "集群的证书", required = false)
    var certificate: String? = null,
    @get:Schema(title = "集群认证用户名", required = false)
    var username: String? = null,
    @get:Schema(title = "集群认证密码", required = false)
    var password: String? = null,
    @get:Schema(title = "集群appId", required = false)
    var appId: String? = null,
    @get:Schema(title = "集群访问凭证", required = false)
    var accessKey: String? = null,
    @get:Schema(title = "集群密钥", required = false)
    var secretKey: String? = null,
    @get:Schema(title = "集群节点类型", required = true)
    var type: ClusterNodeType,
    @get:Schema(title = "创建节点时是否检测连通性", required = false)
    var ping: Boolean = true,
    @get:Schema(title = "连通性检测方式", required = true)
    var detectType: DetectType = DetectType.PING,
    @get:Schema(title = "udp port", required = false)
    var udpPort: Int? = null,
)
