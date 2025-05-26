/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.pojo.node.service

import com.tencent.bkrepo.common.metadata.constant.SYSTEM_USER
import com.tencent.bkrepo.common.metadata.pojo.ServiceRequest
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import com.tencent.bkrepo.common.metadata.pojo.node.NodeRequest
import io.swagger.v3.oas.annotations.media.Schema


@Schema(title = "创建链接节点请求")
data class NodeLinkRequest(
    @get:Schema(title = "所属项目", required = true)
    override val projectId: String,
    @get:Schema(title = "仓库名称", required = true)
    override val repoName: String,
    @get:Schema(title = "完整路径", required = true)
    override val fullPath: String,
    @get:Schema(title = "目标节点所属项目", required = false)
    val targetProjectId: String,
    @get:Schema(title = "目标节点仓库名称", required = false)
    val targetRepoName: String,
    @get:Schema(title = "目标节点完整路径", required = false)
    val targetFullPath: String,
    @get:Schema(title = "是否检查目标节点存在")
    val checkTargetExist: Boolean = true,
    @get:Schema(title = "是否覆盖")
    val overwrite: Boolean = false,
    @get:Schema(title = "元数据信息")
    val nodeMetadata: List<MetadataModel>? = null,
    @get:Schema(title = "操作用户")
    override val operator: String = SYSTEM_USER,
) : NodeRequest, ServiceRequest
