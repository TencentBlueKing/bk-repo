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

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ProjectNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.service.node.NodeSearchService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.replication.constant.MD5
import com.tencent.bkrepo.replication.constant.NODE_FULL_PATH
import com.tencent.bkrepo.replication.constant.SIZE
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 本地数据管理类
 * 用于访问本地集群数据
 */
@Component
class LocalDataManager(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService,
    private val nodeSearchService: NodeSearchService,
    private val packageClient: PackageClient,
    private val storageService: StorageService,
    private val storageCredentialService: StorageCredentialService,
    private val storageProperties: StorageProperties,
    private val mongoTemplate: MongoTemplate,
) {

    /**
     * 获取blob文件数据
     */
    fun getBlobData(sha256: String, length: Long, repoInfo: RepositoryDetail): InputStream {
        val range = Range.full(length)
        return storageService.load(sha256, range, repoInfo.storageCredentials)
            ?: loadFromOtherStorage(sha256, range, repoInfo.storageCredentials)
            ?: throw ArtifactNotFoundException(sha256)
    }

    /**
     * 获取blob文件数据
     */
    fun getBlobDataByRange(sha256: String, range: Range, repoInfo: RepositoryDetail): InputStream {
        return storageService.load(sha256, range, repoInfo.storageCredentials)
            ?: loadFromOtherStorage(sha256, range, repoInfo.storageCredentials)
            ?: throw ArtifactNotFoundException(sha256)
    }

    /**
     * 节点可能是从其他仓库复制过来，仓库存储不一样，对应文件还没有复制
     */
    private fun loadFromOtherStorage(
        sha256: String, range: Range,
        currentStorageCredentials: StorageCredentials?
    ): InputStream? {
        val allCredentials = storageCredentialService.list() + storageProperties.defaultStorageCredentials()
        var result: InputStream? = null
        for (credential in allCredentials) {
            val key = credential.key
            if (key == currentStorageCredentials?.key) continue
            result = storageService.load(sha256, range, credential)
            if (result != null) {
                break
            }
        }
        return result
    }

    /**
     * 查找项目
     * 项目不存在抛异常
     */
    fun findProjectById(projectId: String): ProjectInfo {
        return projectService.getProjectInfo(projectId)
            ?: throw ProjectNotFoundException(projectId)
    }

    /**
     * 判断项目是否存在
     */
    fun existProject(projectId: String): Boolean {
        return projectService.getProjectInfo(projectId) != null
    }

    /**
     * 查找仓库
     * 仓库不存在抛异常
     */
    fun findRepoByName(projectId: String, repoName: String, type: String? = null): RepositoryDetail {
        return repositoryService.getRepoDetail(projectId, repoName, type)
            ?: throw RepoNotFoundException(repoName)
    }

    /**
     * 判断仓库是否存在
     */
    fun existRepo(projectId: String, repoName: String, type: String? = null): Boolean {
        return repositoryService.getRepoDetail(projectId, repoName, type) != null
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
        return nodeService.getDeletedNodeDetail(ArtifactInfo(projectId, repoName, fullPath)).firstOrNull()
    }

    /**
     * 查找节点
     */
    fun findNode(projectId: String, repoName: String, fullPath: String): NodeDetail? {
        return nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
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
        val result = nodeSearchService.searchWithoutCount(queryModel.build())
        if (result.records.isEmpty()) {
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
        val nodes = nodeService.listNode(
            ArtifactInfo(projectId, repoName, fullPath),
            NodeListOption(includeFolder = true, deep = false)
        )
        if (nodes.isNullOrEmpty()) {
            throw NodeNotFoundException("$projectId/$repoName")
        }
        return nodes
    }

    /**
     * 查询目录下的文件列表
     */
    fun listNodePage(
        projectId: String,
        repoName: String,
        fullPath: String,
        pageNumber: Int,
        pageSize: Int,
    ): List<NodeInfo> {
        val collectionName = "node_" + HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)
        val nodePath = PathUtils.toPath(fullPath)
        val criteria = Criteria.where(PROJECT_ID).isEqualTo(projectId)
            .and(REPO_NAME).isEqualTo(repoName)
            .and(DELETED).isEqualTo(null)
            .and(NODE_PATH).isEqualTo(nodePath)

        val query = Query(criteria)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val records = mongoTemplate.find(query.with(pageRequest), Node::class.java, collectionName)
        return records.map { convert(it)!! }
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
        val projectMetrics = projectService.getProjectMetricsInfo(projectId) ?: return 0
        return projectMetrics.repoMetrics.firstOrNull { it.repoName == repoName }?.size ?: 0
    }

    data class Node(
        var createdBy: String,
        var createdDate: LocalDateTime,
        var lastModifiedBy: String,
        var lastModifiedDate: LocalDateTime,
        var lastAccessDate: LocalDateTime? = null,

        var folder: Boolean,
        var path: String,
        var name: String,
        var fullPath: String,
        var size: Long,
        var expireDate: LocalDateTime? = null,
        var sha256: String? = null,
        var md5: String? = null,
        var deleted: LocalDateTime? = null,
        var copyFromCredentialsKey: String? = null,
        var copyIntoCredentialsKey: String? = null,
        var metadata: MutableList<MetadataModel>? = null,
        var clusterNames: Set<String>? = null,
        var nodeNum: Long? = null,
        var archived: Boolean? = null,
        var compressed: Boolean? = null,
        var projectId: String,
        var repoName: String,
        var id: String? = null
    )

    private fun convert(node: Node?): NodeInfo? {
        return node?.let {
            NodeInfo(
                id = it.id,
                createdBy = it.createdBy,
                createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = it.lastModifiedBy,
                lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                projectId = it.projectId,
                repoName = it.repoName,
                folder = it.folder,
                path = it.path,
                name = it.name,
                fullPath = it.fullPath,
                size = if (it.size < 0L) 0L else it.size,
                nodeNum = it.nodeNum?.let { nodeNum ->
                    if (nodeNum < 0L) 0L else nodeNum
                },
                sha256 = it.sha256,
                md5 = it.md5,
                metadata = null,
                nodeMetadata = it.metadata,
                copyFromCredentialsKey = it.copyFromCredentialsKey,
                copyIntoCredentialsKey = it.copyIntoCredentialsKey,
                deleted = it.deleted?.format(DateTimeFormatter.ISO_DATE_TIME),
                lastAccessDate = it.lastAccessDate?.format(DateTimeFormatter.ISO_DATE_TIME),
                clusterNames = it.clusterNames,
                archived = it.archived,
                compressed = it.compressed,
            )
        }
    }

    companion object {
        private const val DELETED = "deleted"
        private const val NODE_PATH = "path"

    }
}
