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

package com.tencent.bkrepo.replication.replica.repository.internal.type

import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.replication.constant.BLOB_PATH_REFRESHED_KEY
import com.tencent.bkrepo.replication.constant.DOCKER_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.DOCKER_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_LAYER_FULL_PATH
import com.tencent.bkrepo.replication.constant.OCI_LAYER_FULL_PATH_V1
import com.tencent.bkrepo.replication.constant.OCI_MANIFEST_JSON_FULL_PATH
import com.tencent.bkrepo.replication.util.ManifestParser
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.springframework.stereotype.Component

/**
 * DOCKER 依赖源需要迁移manifest.json文件以及该文件内容里面包含的config文件和layers文件
 */
@Component
class DockerPackageNodeMapper(
    private val nodeClient: NodeClient,
    private val storageService: StorageService,
    private val repositoryClient: RepositoryClient
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
            var isOci = false
            val repository = repositoryClient.getRepoDetail(projectId, repoName, type.name).data!!
            var manifestFullPath = DOCKER_MANIFEST_JSON_FULL_PATH.format(name, version)
            val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, manifestFullPath).data ?: run {
                // 针对使用oci替换了docker仓库，需要进行数据兼容
                isOci = true
                manifestFullPath = OCI_MANIFEST_JSON_FULL_PATH.format(name, version)
                nodeClient.getNodeDetail(projectId, repoName, manifestFullPath).data!!
            }
            if (nodeDetail.sha256.isNullOrEmpty()) throw ArtifactNotFoundException(manifestFullPath)
            val inputStream = storageService.load(
                nodeDetail.sha256!!,
                Range.full(nodeDetail.size),
                repository.storageCredentials
            )!!
            val manifestInfo = try {
                ManifestParser.parseManifest(inputStream)
            } catch (e: Exception) {
                // 针对v1版本的镜像或者manifest.json文件异常时无法获取到对应的节点列表
                throw ArtifactNotFoundException("Could not read manifest.json, $e")
            }
            manifestInfo!!.descriptors?.forEach {
                result.add(buildBlobPath(
                    descriptor = it,
                    packageName = name,
                    version = version,
                    isOci = isOci,
                    nodeDetail = nodeDetail
                ))
            }
            result.add(manifestFullPath)
            return result
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
            val refreshedMetadata = nodeDetail.nodeMetadata.firstOrNull { it.key == BLOB_PATH_REFRESHED_KEY}
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
