/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.nuget.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.nuget.constant.FULL_PATH
import com.tencent.bkrepo.nuget.constant.NugetMessageCode
import com.tencent.bkrepo.nuget.handler.NugetPackageHandler
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDeleteArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetPublishArtifactInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NugetLocalRepository(
    private val nugetPackageHandler: NugetPackageHandler
) : LocalRepository() {

    override fun onUploadBefore(context: ArtifactUploadContext) {
        super.onUploadBefore(context)
        // 校验版本是否存在，存在则冲突
        with(context.artifactInfo as NugetPublishArtifactInfo) {
            packageClient.findVersionByName(
                projectId, repoName, PackageKeys.ofNuget(packageName.toLowerCase()), version
            ).data?.let {
                throw ErrorCodeException(
                    messageCode = NugetMessageCode.VERSION_EXITED,
                    params = arrayOf(version),
                    status = HttpStatus.CONFLICT
                )
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        with(context.artifactInfo as NugetPublishArtifactInfo) {
            uploadNupkg(context)
            nugetPackageHandler.createPackageVersion(
                context.userId, this, nuspecPackage.metadata, size
            )
            context.response.status = HttpStatus.CREATED.value
            context.response.writer.write("Successfully published NuPkg to: ${getArtifactFullPath()}")
        }
    }

    /**
     * 保存nupkg 文件内容
     */
    private fun uploadNupkg(context: ArtifactUploadContext) {
        val request = buildNodeCreateRequest(context).copy(overwrite = true)
        storageManager.storeArtifactFile(request, context.getArtifactFile(), context.storageCredentials)
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val fullPath = context.getStringAttribute(FULL_PATH).orEmpty()
        with(context) {
            val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
            val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
            val responseName = artifactInfo.getResponseName()
            return ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, useDisposition)
        }
    }

    /**
     * 版本不存在时 status code 404
     */
    override fun remove(context: ArtifactRemoveContext) {
        with(context.artifactInfo as NugetDeleteArtifactInfo) {
            packageClient.findVersionByName(projectId, repoName, packageName, version).data?.let {
                removeVersion(this, it, context.userId)
            } ?: throw VersionNotFoundException("No package with the provided ID and VERSION exists")
        }
        context.response.status = HttpStatus.NO_CONTENT.value
    }

    /**
     * 删除[version] 对应的node节点也会一起删除
     */
    private fun removeVersion(artifactInfo: NugetDeleteArtifactInfo, version: PackageVersion, userId: String) {
        with(artifactInfo) {
            packageClient.deleteVersion(projectId, repoName, packageName, version.name)
            val nugetPath = version.contentPath.orEmpty()
            if (nugetPath.isNotBlank()) {
                val request = NodeDeleteRequest(projectId, repoName, nugetPath, userId)
                nodeClient.deleteNode(request)
            }
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NugetLocalRepository::class.java)
    }
}
