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

package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.constant.PREVIEW_ARTIFACT_TO_FILE
import com.tencent.bkrepo.preview.constant.PREVIEW_NODE_DETAIL
import com.tencent.bkrepo.preview.constant.PREVIEW_TMP_FILE_SAVE_PATH
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewNotFoundException
import com.tencent.bkrepo.preview.pojo.DownloadResult
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.cache.PreviewFileCacheInfo
import com.tencent.bkrepo.preview.utils.DownloadUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component
class FileTransferService(
    private val config: PreviewConfig,
    private val downloadUtils: DownloadUtils,
    private val repositoryService: RepositoryService
) : ArtifactService() {

    /**
     * 输出文件内容
     */
    fun sendFileAsResponse(fileAttribute: FileAttribute, previewFileCacheInfo: PreviewFileCacheInfo) {
        val artifactInfo = ArtifactInfo(
            previewFileCacheInfo.projectId,
            previewFileCacheInfo.repoName,
            previewFileCacheInfo.fullPath
        )
        setFileTransferAttribute(artifactInfo, fileAttribute, false)
        val node = ArtifactContextHolder.getNodeDetail(artifactInfo)
        val context = ArtifactDownloadContext()
        if (node == null && context.repositoryDetail.category == RepositoryCategory.LOCAL) {
            throw PreviewNotFoundException(
                PreviewMessageCode.PREVIEW_NODE_NOT_FOUND,
                "${artifactInfo.projectId}|${artifactInfo.repoName}|${artifactInfo.getArtifactFullPath()}"
            )
        }
        repository.download(context)
    }

    fun download(fileAttribute: FileAttribute): DownloadResult? {
        var result: DownloadResult? = if (fileAttribute.storageType == 1) {
            downloadUtils.downLoad(fileAttribute, config)
        } else {
            downloadFromRepo(fileAttribute)
        }
        fileAttribute.originFilePath = result?.filePath
        return result
    }

    fun downloadFromRepo(fileAttribute: FileAttribute): DownloadResult? {
        val artifactInfo = ArtifactInfo(
            fileAttribute.projectId!!,
            fileAttribute.repoName!!,
            fileAttribute.artifactUri!!
        )
        setFileTransferAttribute(artifactInfo, fileAttribute, true)
        val node = ArtifactContextHolder.getNodeDetail(artifactInfo)
        val context = ArtifactDownloadContext()
        if (node == null && context.repositoryDetail.category == RepositoryCategory.LOCAL) {
            throw PreviewNotFoundException(
                PreviewMessageCode.PREVIEW_NODE_NOT_FOUND,
                "${artifactInfo.projectId}|${artifactInfo.repoName}|${artifactInfo.getArtifactFullPath()}"
            )
        }
        val result = DownloadResult()
        try {
            repository.download(context)
            val request: HttpServletRequest = HttpContextHolder.getRequest()
            result.filePath = request.getAttribute(PREVIEW_TMP_FILE_SAVE_PATH)?.toString()
            result.md5 = node!!.md5
            result.size = node.size
        } catch (e: Exception) {
            result.apply {
                code = DownloadResult.CODE_FAIL
                msg = "Download Faile from bkrepo,$e"
            }
        }

        return result
    }

    fun upload(fileAttribute: FileAttribute, sourcePath: String): NodeDetail {
        val file = File(sourcePath)
        require(file.exists()) { "The file does not exist, $sourcePath" }
        // 准备要上传的信息,如果是bkrepo文件，预览文件保存在原仓库，否则保存在自定义仓库中
        val projectId = config.projectId
        val repoName = config.repoName
        val artifactInfo = ArtifactInfo(projectId!!, repoName!!, buildArtifactUri(fileAttribute))
        setFileTransferAttribute(artifactInfo, fileAttribute, true)
        val artifactFile = ArtifactFileFactory.build(file.inputStream(), file.length())
        val context = ArtifactUploadContext(artifactFile)

        repository.upload(context)

        val nodeDetail: NodeDetail = context.getAttribute(PREVIEW_NODE_DETAIL)!!
        return nodeDetail
    }

    /**
     * 把project、repo等信息设置到request域，上传、下载要用
     */
    private fun setFileTransferAttribute(
        artifactInfo: ArtifactInfo,
        fileAttribute: FileAttribute,
        toFile: Boolean
    ) {
        val request: HttpServletRequest = HttpContextHolder.getRequest()
        val repoDetail = repositoryService.getRepoDetail(
            artifactInfo.projectId,
            artifactInfo.repoName,
            RepositoryType.GENERIC.name
        ) ?: throw PreviewNotFoundException(
            PreviewMessageCode.PREVIEW_REPO_NOT_FOUND,
            "${artifactInfo.projectId}|${artifactInfo.repoName}"
        )
        request.setAttribute(ARTIFACT_INFO_KEY, artifactInfo)
        request.setAttribute(REPO_KEY, repoDetail)

        val fileTmpPath = downloadUtils.getRelFilePath(fileAttribute.fileName, fileAttribute.suffix!!, config.fileDir)
        request.setAttribute(PREVIEW_TMP_FILE_SAVE_PATH, fileTmpPath)
        request.setAttribute(PREVIEW_ARTIFACT_TO_FILE, toFile)
    }

    /**
     * 制品uri：/date/uuid/fileName
     */
    private fun buildArtifactUri(fileAttribute: FileAttribute): String {
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return if (fileAttribute.storageType == 0)
            "/convert/$date/${UUID.randomUUID()}/${fileAttribute.convertFileName}"
        else
            "/$date/${UUID.randomUUID()}/${fileAttribute.convertFileName}"
    }

}
