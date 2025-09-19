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

package com.tencent.bkrepo.replication.replica.repository.internal.type

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.replication.constant.BLOB_PATH_REFRESHED_KEY
import com.tencent.bkrepo.replication.constant.DOCKER_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.DOCKER_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_LAYER_FULL_PATH_V1
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_LIST
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_LIST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.util.ManifestParser
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.springframework.stereotype.Component
import java.io.InputStream

/**
 * DOCKER 依赖源需要迁移manifest.json文件以及该文件内容里面包含的config文件和layers文件
 */
@Component
class DockerPackageNodeMapper(
    private val nodeService: NodeService,
    private val storageManager: StorageManager,
    private val repositoryService: RepositoryService
) : PackageNodeMapper {

    override fun type() = RepositoryType.DOCKER
    override fun extraType(): RepositoryType? {
        return RepositoryType.OCI
    }

    override fun map(
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
        type: RepositoryType
    ): List<String> {
        with(packageSummary) {
            val result = mutableListOf<String>()
            val name = packageSummary.name
            val version = packageVersion.name
            val repository = repositoryService.getRepoDetail(projectId, repoName, type.name)!!
            // 尝试获取manifest节点详情，支持Docker和OCI格式的兼容
            val (nodeDetail, manifestFullPath, isOci) = findManifestNode(
                projectId = projectId,
                repoName = repoName,
                packageName = name,
                version = version,
                packageVersion = packageVersion
            )
            // 验证节点是否存在
            if (nodeDetail?.sha256.isNullOrEmpty()) {
                throw ArtifactNotFoundException(manifestFullPath)
            }
            // 加载manifest内容并解析依赖
            val inputStream = storageManager.loadFullArtifactInputStream(nodeDetail, repository.storageCredentials)!!
            // list.manifest.json 只需要分发本身，不需要解析内部依赖
            if (nodeDetail!!.name != OCI_MANIFEST_LIST) {
                addManifestDependencies(inputStream, result, name, version, isOci, nodeDetail)
            }
            result.add(manifestFullPath)
            return result
        }
    }

    /**
     * 查找manifest节点，支持Docker和OCI格式的兼容
     * @return Triple<NodeDetail?, String, Boolean> - (节点详情, manifest路径, 是否为OCI格式)
     */
    private fun findManifestNode(
        projectId: String,
        repoName: String,
        packageName: String,
        version: String,
        packageVersion: PackageVersion
    ): Triple<NodeDetail?, String, Boolean> {
        // 首先尝试Docker格式
        val dockerManifestPath = DOCKER_MANIFEST_JSON_FULL_PATH.format(packageName, version)
        val dockerNodeDetail = getNodeDetail(projectId, repoName, dockerManifestPath)
        if (dockerNodeDetail != null) {
            return Triple(dockerNodeDetail, dockerManifestPath, false)
        }

        // Docker格式不存在，尝试OCI格式
        return if (packageVersion.manifestPath.isNullOrEmpty()) {
            // 使用默认OCI路径
            findOciManifestWithDefaultPaths(projectId, repoName, packageName, version)
        } else {
            // 使用指定的manifest路径
            val specifiedPath = packageVersion.manifestPath!!
            val nodeDetail = getNodeDetail(projectId, repoName, specifiedPath)
            Triple(nodeDetail, specifiedPath, true)
        }
    }

    /**
     * 使用默认OCI路径查找manifest节点
     */
    private fun findOciManifestWithDefaultPaths(
        projectId: String,
        repoName: String,
        packageName: String,
        version: String
    ): Triple<NodeDetail?, String, Boolean> {
        // 尝试标准OCI manifest路径
        val ociManifestPath = OCI_MANIFEST_JSON_FULL_PATH.format(packageName, version)
        val ociNodeDetail = getNodeDetail(projectId, repoName, ociManifestPath)
        if (ociNodeDetail != null) {
            return Triple(ociNodeDetail, ociManifestPath, true)
        }

        // 尝试OCI manifest list路径
        val ociListPath = OCI_MANIFEST_LIST_JSON_FULL_PATH.format(packageName, version)
        val ociListNodeDetail = getNodeDetail(projectId, repoName, ociListPath)
        return Triple(ociListNodeDetail, ociListPath, true)
    }

    /**
     * 获取节点详情的工具方法
     */
    private fun getNodeDetail(projectId: String, repoName: String, fullPath: String): NodeDetail? {
        return nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
    }

    /**
     * 添加manifest的依赖文件（config和layers）到结果列表
     */
    private fun addManifestDependencies(
        inputStream: InputStream,
        result: MutableList<String>,
        packageName: String,
        version: String,
        isOci: Boolean,
        nodeDetail: NodeDetail
    ) {
        val manifestInfo = try {
            ManifestParser.parseManifest(inputStream)
        } catch (e: Exception) {
            // 针对v1版本的镜像或者manifest.json文件异常时无法获取到对应的节点列表
            throw ArtifactNotFoundException("Could not read manifest.json, $e")
        }

        manifestInfo?.descriptors?.forEach { descriptor ->
            result.add(
                buildBlobPath(
                    descriptor = descriptor,
                    packageName = packageName,
                    version = version,
                    isOci = isOci,
                    nodeDetail = nodeDetail
                )
            )
        }
    }

    /**
     * 通过包名、版本、sha256拼接出blob路径
     */
    private fun buildBlobPath(
        descriptor: String,
        packageName: String,
        version: String,
        isOci: Boolean,
        nodeDetail: NodeDetail
    ): String {
        val replace = descriptor.replace(":", "__")
        return if (isOci) {
            // 镜像blob路径格式有调整，从/package/blobs/下调至//package/blobs/version/
            val refreshedMetadata = nodeDetail.nodeMetadata.firstOrNull { it.key == BLOB_PATH_REFRESHED_KEY }
            if (refreshedMetadata != null) {
                OCI_LAYER_FULL_PATH_V1.format(packageName, version, replace)
            } else {
                OCI_LAYER_FULL_PATH.format(packageName, replace)
            }
        } else {
            DOCKER_LAYER_FULL_PATH.format(packageName, version, replace)
        }
    }
}
