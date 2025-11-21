/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.replicator

import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteSummary
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion

/**
 * 同步器
 */
interface Replicator {

    /**
     * 检查版本
     */
    fun checkVersion(context: ReplicaContext)

    /**
     * 同步项目
     */
    fun replicaProject(context: ReplicaContext)

    /**
     * 同步仓库
     */
    fun replicaRepo(context: ReplicaContext)

    /**
     * 同步包
     */
    fun replicaPackage(context: ReplicaContext, packageSummary: PackageSummary)

    /**
     * 同步包版本具体逻辑
     * @return 是否执行了同步，如果远程存在相同版本，则返回false
     */
    fun replicaPackageVersion(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion
    ): Boolean

    /**
     * 删除package
     */
    fun replicaDeletedPackage(
        context: ReplicaContext,
        packageVersionDeleteSummary: PackageVersionDeleteSummary
    ): Boolean

    /**
     * 同步文件
     * @return 是否执行了同步，如果远程存在相同文件，则返回false
     */
    fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean

    /**
     * 同步目录节点
     * @return 是否执行了同步，如果远程存在相同目录，则返回false
     */
    fun replicaDir(context: ReplicaContext, node: NodeInfo)

    /**
     * 同步已删除节点
     */
    fun replicaDeletedNode(context: ReplicaContext, node: NodeInfo): Boolean

    /**
     * 同步node move操作
     */
    fun replicaNodeMove(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean

    /**
     * 同步node copy操作
     */
    fun replicaNodeCopy(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean

    /**
     * 同步node name操作
     */
    fun replicaNodeRename(context: ReplicaContext, nodeRenameRequest: NodeRenameRequest): Boolean

    /**
     * 同步metadata save操作
     */
    fun replicaMetadataSave(context: ReplicaContext, metadataSaveRequest: MetadataSaveRequest): Boolean

    /**
     * 同步metadata delete操作
     */
    fun replicaMetadataDelete(context: ReplicaContext, metadataDeleteRequest: MetadataDeleteRequest): Boolean

    /**
     * 检查node是否存在
     * @param context 同步上下文
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param fullPath 节点完整路径
     * @param deleted 删除时间（可选，如果提供则检查已删除的节点）
     * @return 如果节点存在返回true，否则返回false
     */
    fun checkNodeExist(
        context: ReplicaContext,
        projectId: String,
        repoName: String,
        fullPath: String,
        deleted: String? = null
    ): Boolean

    /**
     * 检查packageVersion是否存在
     * @param context 同步上下文
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param packageKey 包键
     * @param versionName 版本名称
     * @return 如果包版本存在返回true，否则返回false
     */
    fun checkPackageVersionExist(
        context: ReplicaContext,
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String
    ): Boolean
}
