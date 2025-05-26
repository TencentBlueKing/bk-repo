/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.pojo.node

import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_PACKAGE_NAME
import com.tencent.bkrepo.common.artifact.constant.METADATA_KEY_PACKAGE_VERSION
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 节点详细信息
 */
@Schema(title = "节点详细信息")
data class NodeDetail(
    @get:Schema(title = "节点基本信息")
    @Deprecated("冗余信息，nodeInfo信息已包含在NodeDetail字段中，nodeInfo将来会删除")
    val nodeInfo: NodeInfo,

    @get:Schema(title = "创建者")
    val createdBy: String = nodeInfo.createdBy,
    @get:Schema(title = "创建时间")
    val createdDate: String = nodeInfo.createdDate,
    @get:Schema(title = "修改者")
    val lastModifiedBy: String = nodeInfo.lastModifiedBy,
    @get:Schema(title = "修改时间")
    val lastModifiedDate: String = nodeInfo.lastModifiedDate,
    @get:Schema(title = "访问时间")
    val lastAccessDate: String? = nodeInfo.lastAccessDate,

    @get:Schema(title = "是否为文件夹")
    val folder: Boolean = nodeInfo.folder,
    @get:Schema(title = "路径")
    val path: String = nodeInfo.path,
    @get:Schema(title = "资源名称")
    val name: String = nodeInfo.name,
    @get:Schema(title = "完整路径")
    val fullPath: String = nodeInfo.fullPath,
    @get:Schema(title = "文件大小，单位byte")
    val size: Long = nodeInfo.size,
    @get:Schema(title = "文件节点个数")
    val nodeNum: Long? = nodeInfo.nodeNum,
    @get:Schema(title = "文件sha256")
    val sha256: String? = nodeInfo.sha256,
    @get:Schema(title = "文件md5")
    val md5: String? = nodeInfo.md5,
    @get:Schema(title = "元数据")
    val metadata: Map<String, Any> = nodeInfo.metadata.orEmpty(),
    @get:Schema(title = "元数据")
    val nodeMetadata: List<MetadataModel> = nodeInfo.nodeMetadata.orEmpty(),
    @get:Schema(title = "所属项目id")
    val projectId: String = nodeInfo.projectId,
    @get:Schema(title = "所属仓库名称")
    val repoName: String = nodeInfo.repoName,
    @get:Schema(title = "集群信息")
    val clusterNames: Set<String>? = nodeInfo.clusterNames,
    @get:Schema(title = "是否归档")
    val archived: Boolean? = nodeInfo.archived,
    @get:Schema(title = "是否压缩")
    val compressed: Boolean? = nodeInfo.compressed,
) {
    /**
     * 获取node所属package的name
     */
    fun packageName() = metadata[METADATA_KEY_PACKAGE_NAME]?.toString()

    /**
     * 获取node所属package的版本
     */
    fun packageVersion() = metadata[METADATA_KEY_PACKAGE_VERSION]?.toString()

    fun identity(): String = "$projectId/$repoName/$fullPath"
}