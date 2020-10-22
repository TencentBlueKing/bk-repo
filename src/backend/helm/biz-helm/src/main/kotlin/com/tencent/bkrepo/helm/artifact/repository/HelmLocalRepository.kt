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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.exception.HelmFileAlreadyExistsException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HelmLocalRepository : LocalRepository() {

    override fun onUploadBefore(context: ArtifactUploadContext) {
        // 判断是否是强制上传
        val isForce = context.request.getParameter("force")?.let { true } ?: false
        context.putAttribute("force", isForce)
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        context.getArtifactFileMap().entries.forEach { (name, _) ->
            val fullPath = context.getStringAttribute(name + "_full_path")!!
            val isExist = nodeClient.exist(projectId, repoName, fullPath).data!!
            if (isExist && !isOverwrite(fullPath, isForce)) {
                throw HelmFileAlreadyExistsException("${fullPath.trimStart('/')} already exists")
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        context.getArtifactFileMap().entries.forEach { (name, _) ->
            val nodeCreateRequest = getNodeCreateRequest(name, context)
            storageManager.storeArtifactFile(
                nodeCreateRequest,
                context.getArtifactFile(name),
                context.storageCredentials
            )
        }
    }

    private fun getNodeCreateRequest(name: String, context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryDetail = context.repositoryDetail
        val artifactFile = context.getArtifactFile(name)
        val sha256 = context.getArtifactSha256(name)
        val md5 = context.getArtifactMd5(name)
        val fullPath = context.getStringAttribute(name + FULL_PATH)!!
        val isForce = context.getBooleanAttribute("force")!!
        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId,
            metadata = parseMetaData(fullPath, isForce),
            overwrite = isOverwrite(fullPath, isForce)
        )
    }

    private fun parseMetaData(fullPath: String, isForce: Boolean): Map<String, String>? {
        if (isOverwrite(fullPath, isForce) || !fullPath.endsWith(".tgz")) {
            return emptyMap()
        }
        val substring = fullPath.trimStart('/').substring(0, fullPath.lastIndexOf('.') - 1)
        val name = substring.substringBeforeLast('-')
        val version = substring.substringAfterLast('-')
        return mapOf("name" to name, "version" to version)
    }

    private fun isOverwrite(fullPath: String, isForce: Boolean): Boolean {
        return isForce || !(fullPath.trim().endsWith(".tgz", true) || fullPath.trim().endsWith(".prov", true))
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        with(context) {
            val node = nodeClient.detail(projectId, repoName, fullPath).data
            if (node == null || node.folder) return null
            node.metadata[NAME]?.let { context.putAttribute(NAME, it) }
            node.metadata[VERSION]?.let { context.putAttribute(VERSION, it) }
            val range = resolveRange(context, node.size)
            val inputStream = storageService.load(node.sha256!!, range, storageCredentials) ?: return null
            return ArtifactResource(
                inputStream,
                artifactInfo.getResponseName(),
                node,
                ArtifactChannel.LOCAL,
                useDisposition
            )
        }
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): DownloadStatisticsAddRequest? {
        val name = context.getStringAttribute(NAME).orEmpty()
        val version = context.getStringAttribute(VERSION).orEmpty()
        with(context) {
            return DownloadStatisticsAddRequest(projectId, repoName, PackageKeys.ofHelm(name), name, version)
        }
    }

    override fun query(context: ArtifactQueryContext): ArtifactInputStream? {
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        return this.onQuery(context) ?: throw HelmFileNotFoundException("Artifact[$fullPath] does not exist")
    }

    private fun onQuery(context: ArtifactQueryContext): ArtifactInputStream? {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        val node = nodeClient.detail(projectId, repoName, fullPath).data
        if (node == null || node.folder) return null
        return storageService.load(
            node.sha256!!, Range.full(node.size), context.storageCredentials
        )?.also { logger.info("search artifact [$fullPath] success!") }
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getAttribute<List<String>>(FULL_PATH).orEmpty()
        val userId = context.userId
        fullPath.forEach {
            nodeClient.delete(NodeDeleteRequest(projectId, repoName, it, userId))
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmLocalRepository::class.java)
    }
}
