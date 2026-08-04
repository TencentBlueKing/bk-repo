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

package com.tencent.bkrepo.opdata.cluster.topology.pojo

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "节点元数据视图")
data class ClusterNodeMetadataVO(
    @get:Schema(title = "集群名称") val clusterName: String,
    @get:Schema(title = "集群访问地址") val url: String?,
    @get:Schema(title = "集群类型") val type: ClusterNodeType,
    @get:Schema(title = "地域") val region: String?,
    @get:Schema(title = "网络区域") val networkZone: String?,
    @get:Schema(title = "展示名") val displayName: String?,
    @get:Schema(title = "描述") val description: String?,
    @get:Schema(title = "最近修改人") val lastModifiedBy: String?,
    @get:Schema(title = "最近修改时间") val lastModifiedDate: LocalDateTime?
)

@Schema(title = "节点元数据更新请求")
data class ClusterNodeMetadataUpdateRequest(
    @get:Schema(title = "地域，可清空") val region: String? = null,
    @get:Schema(title = "网络区域，可清空") val networkZone: String? = null,
    @get:Schema(title = "展示名，可清空") val displayName: String? = null,
    @get:Schema(title = "描述，可清空") val description: String? = null
)
