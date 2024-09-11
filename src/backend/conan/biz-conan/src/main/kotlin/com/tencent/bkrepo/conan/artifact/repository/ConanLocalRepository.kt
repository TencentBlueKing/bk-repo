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
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.conan.constant.CONANFILE
import com.tencent.bkrepo.conan.constant.EXPORT_SOURCES_TGZ_NAME
import com.tencent.bkrepo.conan.constant.NAME
import com.tencent.bkrepo.conan.constant.PACKAGE_TGZ_NAME
import com.tencent.bkrepo.conan.constant.VERSION
import com.tencent.bkrepo.conan.listener.event.ConanPackageUploadEvent
import com.tencent.bkrepo.conan.listener.event.ConanRecipeUploadEvent
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.buildDownloadResponse
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.buildPackageUpdateRequest
import com.tencent.bkrepo.conan.utils.ObjectBuildUtil.buildPackageVersionCreateRequest
import com.tencent.bkrepo.conan.utils.PathUtils.generateFullPath
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConanLocalRepository : LocalRepository() {

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        with(context) {
            val fullPath = generateFullPath(context.artifactInfo as ConanArtifactInfo)
            logger.info("File $fullPath will be stored in $projectId|$repoName")
            return NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = fullPath,
                size = getArtifactFile().getSize(),
                sha256 = getArtifactSha256(),
                md5 = getArtifactMd5(),
                operator = userId,
                overwrite = true
            )
        }
    }

    /**
     * 上传成功回调
     */
    override fun onUploadSuccess(context: ArtifactUploadContext) {
        super.onUploadSuccess(context)
        val fullPath = generateFullPath(context.artifactInfo as ConanArtifactInfo)
        if (fullPath.endsWith(CONANFILE)) {
            // TODO package version size 如何计算
            createVersion(
                artifactInfo = context.artifactInfo as ConanArtifactInfo,
                userId = context.userId,
                size = 0
            )
            publishEvent(
                ConanRecipeUploadEvent(
                    ObjectBuildUtil.buildConanRecipeUpload(context.artifactInfo as ConanArtifactInfo, context.userId)
                )
            )
        }
        if (fullPath.endsWith(PACKAGE_TGZ_NAME)) {
            publishEvent(
                ConanPackageUploadEvent(
                    ObjectBuildUtil.buildConanPackageUpload(context.artifactInfo as ConanArtifactInfo, context.userId)
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
        // TODO 元数据中要加入对应username与channel，可能存在同一制品版本存在不同username与channel
        val packageUpdateRequest = buildPackageUpdateRequest(artifactInfo)
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
                node.metadata[NAME]?.let { context.putAttribute(NAME, it) }
                node.metadata[VERSION]?.let { context.putAttribute(VERSION, it) }
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
        // TODO 需要判断只有下载包时才统计次数
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConanLocalRepository::class.java)
    }
}
