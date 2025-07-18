/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.pojo.node

import com.fasterxml.jackson.annotation.JsonInclude
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import io.swagger.v3.oas.annotations.media.Schema


/**
 * 节点信息
 */
@Schema(title = "节点信息")
data class NodeInfo(
    var id: String? = null,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: String,
    @get:Schema(title = "修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "修改时间")
    val lastModifiedDate: String,
    @get:Schema(title = "访问时间")
    val lastAccessDate: String? = null,

    @get:Schema(title = "是否为文件夹")
    val folder: Boolean,
    @get:Schema(title = "路径")
    val path: String,
    @get:Schema(title = "资源名称")
    val name: String,
    @get:Schema(title = "完整路径")
    val fullPath: String,
    @get:Schema(title = "文件大小，单位byte")
    val size: Long,
    @get:Schema(title = "文件节点个数")
    val nodeNum: Long? = null,
    @get:Schema(title = "文件sha256")
    val sha256: String? = null,
    @get:Schema(title = "文件md5")
    val md5: String? = null,
    @get:Schema(title = "元数据")
    val metadata: Map<String, Any>? = null,
    @get:Schema(title = "元数据信息")
    val nodeMetadata: List<MetadataModel>? = null,
    @get:Schema(title = "所属项目id")
    val projectId: String,
    @get:Schema(title = "所属仓库名称")
    val repoName: String,
    @get:Schema(title = "拷贝的源存储key")
    val copyFromCredentialsKey: String? = null,
    @get:Schema(title = "拷贝的目标存储key")
    val copyIntoCredentialsKey: String? = null,
    @get:Schema(title = "删除时间")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val deleted: String? = null,
    @get:Schema(title = "集群信息")
    val clusterNames: Set<String>? = null,
    @get:Schema(title = "是否归档")
    val archived: Boolean? = null,
    @get:Schema(title = "是否压缩")
    val compressed: Boolean? = null,
    @get:Schema(title = "联邦仓库同步来源集群id")
    val federatedSource: String? = null,
)
