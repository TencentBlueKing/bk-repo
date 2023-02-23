/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
/**
 * 更新集群节点请求
 */
@ApiModel("更新集群节点请求")
data class ClusterNodeUpdateRequest(
    @ApiModelProperty("添加的集群名称", required = true)
    var name: String,
    @ApiModelProperty("集群地址", required = false)
    var url: String? = null,
    @ApiModelProperty("集群的证书", required = false)
    var certificate: String? = null,
    @ApiModelProperty("集群认证用户名", required = false)
    var username: String? = null,
    @ApiModelProperty("集群认证密码", required = false)
    var password: String? = null,
    @ApiModelProperty("集群节点类型", required = true)
    var type: ClusterNodeType
)
