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

package com.tencent.bkrepo.conan.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.conan.constant.CONAN_MANIFEST
import com.tencent.bkrepo.conan.constant.X_CHECKSUM_SHA1
import com.tencent.bkrepo.conan.listener.event.ConanPackageUploadEvent
import com.tencent.bkrepo.conan.listener.event.ConanRecipeUploadEvent
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil
import com.tencent.bkrepo.conan.utils.ConanArtifactInfoUtil.convertToConanFileReference
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.buildDownloadResponse
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.buildPackageVersionCreateRequest
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.toConanFileReference
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.toMetadataList
import com.tencent.bkrepo.conan.utils.ConanPathUtils
import com.tencent.bkrepo.conan.utils.ConanPathUtils.generateFullPath
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConanLocalRepository : LocalRepository() {

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        with(context) {
            val tempArtifactInfo = context.artifactInfo as ConanArtifactInfo
            val fullPath = generateFullPath(tempArtifactInfo)
            logger.info("File $fullPath will be stored in $projectId|$repoName")
            val sha1 = HttpContextHolder.getRequest().getHeader(X_CHECKSUM_SHA1)?.toString()
            val conanFileReference = convertToConanFileReference(tempArtifactInfo)
            val metadata = mutableListOf<MetadataModel>()
            sha1?.let {
                metadata.add(MetadataModel(key = X_CHECKSUM_SHA1, value = sha1, system = true))
            }
            metadata.addAll(conanFileReference.toMetadataList())
            return NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = fullPath,
                size = getArtifactFile().getSize(),
                sha256 = getArtifactSha256(),
                md5 = getArtifactMd5(),
                operator = userId,
                overwrite = true,
                nodeMetadata = metadata
            )
        }
    }

    /**
     * 上传成功回调
     */
    override fun onUploadSuccess(context: ArtifactUploadContext) {
        super.onUploadSuccess(context)
        val fullPath = generateFullPath(context.artifactInfo as ConanArtifactInfo)
        val artifactInfo = context.artifactInfo as ConanArtifactInfo
        if (fullPath.endsWith(CONAN_MANIFEST) && artifactInfo.packageId.isNullOrEmpty()) {
            //  package version size 为manifest文件大小
            createVersion(
                artifactInfo = artifactInfo,
                userId = context.userId,
                size = context.getArtifactFile().getSize()
            )
            publishEvent(
                ConanRecipeUploadEvent(
                    ObjectBuildUtil.buildConanRecipeUpload(artifactInfo, context.userId)
                )
            )
        }
        if (fullPath.endsWith(CONAN_MANIFEST) && !artifactInfo.packageId.isNullOrEmpty()) {
            publishEvent(
                ConanPackageUploadEvent(
                    ObjectBuildUtil.buildConanPackageUpload(artifactInfo, context.userId)
                )
            )
        }
    }

    /**
     * 创建包版本
     */
    fun createVersion(
        userId: String,
        artifactInfo: ConanArtifactInfo,
        size: Long,
        sourceType: ArtifactChannel? = null
    ) {
        val packageVersionCreateRequest = buildPackageVersionCreateRequest(
            userId = userId,
            artifactInfo = artifactInfo,
            size = size,
            sourceType = sourceType
        )
        packageClient.createVersion(packageVersionCreateRequest).apply {
            logger.info("user: [$userId] create package version [$packageVersionCreateRequest] success!")
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context.artifactInfo as ConanArtifactInfo) {
            val fullPath = generateFullPath(this)
            logger.info("File $fullPath will be downloaded in repo $projectId|$repoName")
            val node = nodeClient.getNodeDetail(context.projectId, context.repoName, fullPath).data
            node?.let {
                context.artifactInfo.setArtifactMappingUri(node.fullPath)
                downloadIntercept(context, node)
                packageVersion(context, node)?.let { packageVersion -> downloadIntercept(context, packageVersion) }
            }
            val inputStream = storageManager.loadArtifactInputStream(node, context.storageCredentials)
            buildDownloadResponse()
            inputStream?.let {
                return ArtifactResource(
                    inputStream,
                    context.artifactInfo.getResponseName(),
                    node,
                    ArtifactChannel.LOCAL,
                    context.useDisposition
                )
            }
            return null
        }
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        val conanFileReference = ConanArtifactInfoUtil.convertToConanFileReference(
            context.artifactInfo as ConanArtifactInfo
        )
        val refStr = ConanPathUtils.buildReferenceWithoutVersion(conanFileReference)
        return PackageDownloadRecord(
            context.projectId, context.repoName, PackageKeys.ofConan(refStr), conanFileReference.version
        )
    }

    private fun packageVersion(context: ArtifactDownloadContext, node: NodeDetail): PackageVersion? {
        with(context) {
            val conanFileReference = node.nodeMetadata.toConanFileReference() ?: return null
            val refStr = ConanPathUtils.buildReferenceWithoutVersion(conanFileReference)
            val packageKey = PackageKeys.ofConan(refStr)
            return packageClient.findVersionByName(projectId, repoName, packageKey, conanFileReference.version).data
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConanLocalRepository::class.java)
    }
}
