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

package com.tencent.bkrepo.replication.manager

import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ProjectNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.replication.constant.MD5
import com.tencent.bkrepo.replication.constant.NODE_FULL_PATH
import com.tencent.bkrepo.replication.constant.SIZE
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.springframework.stereotype.Component
import java.io.InputStream

/**
 * 本地数据管理类
 * 用于访问本地集群数据
 */
@Component
class LocalDataManager(
    private val projectClient: ProjectClient,
    private val repositoryClient: RepositoryClient,
    private val nodeClient: NodeClient,
    private val packageClient: PackageClient,
    private val storageService: StorageService
) {

    /**
     * 获取blob文件数据
     */
    fun getBlobData(sha256: String, length: Long, repoInfo: RepositoryDetail): InputStream {
        return storageService.load(sha256, Range.full(length), repoInfo.storageCredentials)
            ?: throw ArtifactNotFoundException(sha256)
    }

    /**
     * 获取blob文件数据
     */
    fun getBlobDataByRange(sha256: String, range: Range, repoInfo: RepositoryDetail): InputStream {
        return storageService.load(sha256, range, repoInfo.storageCredentials)
            ?: throw ArtifactNotFoundException(sha256)
    }

    /**
     * 查找项目
     * 项目不存在抛异常
     */
    fun findProjectById(projectId: String): ProjectInfo {
        return projectClient.getProjectInfo(projectId).data
            ?: throw ProjectNotFoundException(projectId)
    }

    /**
     * 判断项目是否存在
     */
    fun existProject(projectId: String): Boolean {
        return projectClient.getProjectInfo(projectId).data != null
    }

    /**
     * 查找仓库
     * 仓库不存在抛异常
     */
    fun findRepoByName(projectId: String, repoName: String, type: String? = null): RepositoryDetail {
        return repositoryClient.getRepoDetail(projectId, repoName, type).data
            ?: throw RepoNotFoundException(repoName)
    }

    /**
     * 判断仓库是否存在
     */
    fun existRepo(projectId: String, repoName: String, type: String? = null): Boolean {
        return repositoryClient.getRepoDetail(projectId, repoName, type).data != null
    }

    /**
     * 根据packageKey查找包信息
     */
    fun findPackageByKey(projectId: String, repoName: String, packageKey: String): PackageSummary {
        return packageClient.findPackageByKey(projectId, repoName, packageKey).data
            ?: throw PackageNotFoundException(packageKey)
    }

    /**
     * 查询所有版本
     */
    fun listAllVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        option: VersionListOption
    ): List<PackageVersion> {
        return packageClient.listAllVersion(projectId, repoName, packageKey, option).data
            ?: throw PackageNotFoundException(packageKey)
    }

    /**
     * 查询指定版本
     */
    fun findPackageVersion(projectId: String, repoName: String, packageKey: String, version: String): PackageVersion {
        return packageClient.findVersionByName(projectId, repoName, packageKey, version).data
            ?: throw VersionNotFoundException(packageKey)
    }

    /**
     * 查找节点
     */
    fun findNodeDetail(projectId: String, repoName: String, fullPath: String): NodeDetail {
        return findNode(projectId, repoName, fullPath)
            ?: throw NodeNotFoundException(fullPath)
    }


    /**
     * 查找package对应version下的节点
     */
    fun findNodeDetailInVersion(
        projectId: String, repoName: String, fullPath: String
    ): NodeDetail {
        return findNode(projectId, repoName, fullPath) ?: findDeletedNodeDetail(projectId, repoName, fullPath)
            ?: throw NodeNotFoundException(fullPath)
    }
    fun findDeletedNodeDetail(
        projectId: String, repoName: String, fullPath: String
    ): NodeDetail? {
        return nodeClient.getDeletedNodeDetail(projectId, repoName, fullPath).data?.firstOrNull()
    }

    /**
     * 查找节点
     */
    fun findNode(projectId: String, repoName: String, fullPath: String): NodeDetail? {
        return nodeClient.getNodeDetail(projectId, repoName, fullPath).data
    }

    /**
     * 根据sha256获取对应节点大小
     */
    fun getNodeBySha256(
        projectId: String,
        repoName: String,
        sha256: String
    ): FileInfo {
        val queryModel = NodeQueryBuilder()
            .select(NODE_FULL_PATH, SIZE, MD5)
            .projectId(projectId)
            .repoName(repoName)
            .sha256(sha256)
            .page(1, 1)
            .sortByAsc(NODE_FULL_PATH)
        val result = nodeClient.queryWithoutCount(queryModel.build()).data
        if (result == null || result.records.isEmpty()) {
            throw NodeNotFoundException(sha256)
        }
        return FileInfo(
            sha256 = sha256,
            md5 = result.records[0][MD5].toString(),
            size = result.records[0][SIZE].toString().toLong()
        )
    }

/**
     * 分页查询包
     */
    fun listPackagePage(projectId: String, repoName: String, option: PackageListOption): List<PackageSummary> {
        val packages = packageClient.listPackagePage(
            projectId = projectId,
            repoName = repoName,
            option = option
        ).data?.records
        if (packages.isNullOrEmpty()) {
            return emptyList()
        }
        return packages
    }

    /**
     * 查询目录下的文件列表
     */
    fun listNode(projectId: String, repoName: String, fullPath: String): List<NodeInfo> {
        val nodes = nodeClient.listNode(
            projectId = projectId,
            repoName = repoName,
            path = fullPath,
            includeFolder = true,
            deep = false
        ).data
        if (nodes.isNullOrEmpty()) {
            throw NodeNotFoundException("$projectId/$repoName")
        }
        return nodes
    }

    /**
     * 根据节点信息读取节点的数据流
     */
    fun loadInputStream(nodeInfo: NodeDetail): InputStream {
        with(nodeInfo) {
            return loadInputStream(sha256!!, size, projectId, repoName)
        }
    }

    /**
     * 根据项目、仓库、sha256、size读取对应节点的数据流
     */
    fun loadInputStream(sha256: String, size: Long, projectId: String, repoName: String): InputStream {
        val repo = findRepoByName(projectId, repoName)
        return getBlobData(sha256, size, repo)
    }

    /**
     * 根据项目、仓库、sha256、range读取对应节点的数据流
     */
    fun loadInputStreamByRange(sha256: String, range: Range, projectId: String, repoName: String): InputStream {
        val repo = findRepoByName(projectId, repoName)
        return getBlobDataByRange(sha256, range, repo)
    }


    /**
     * 从项目仓库统计信息中找出对应仓库的大小
     */
    fun getRepoMetricInfo(projectId: String, repoName: String): Long {
        findRepoByName(projectId, repoName)
        val projectMetrics = projectClient.getProjectMetrics(projectId).data ?: return 0
        return projectMetrics.repoMetrics.firstOrNull { it.repoName == repoName }?.size ?: 0
    }
}
