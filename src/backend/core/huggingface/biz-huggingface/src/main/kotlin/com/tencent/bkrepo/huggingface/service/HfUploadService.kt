/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.StreamUtils.readText
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJson
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.huggingface.artifact.HuggingfaceArtifactInfo
import com.tencent.bkrepo.huggingface.constants.BASE64_ENCODING
import com.tencent.bkrepo.huggingface.constants.COMMIT_ID_HEADER
import com.tencent.bkrepo.huggingface.constants.COMMIT_OP_DEL_FILE
import com.tencent.bkrepo.huggingface.constants.COMMIT_OP_DEL_FOLDER
import com.tencent.bkrepo.huggingface.constants.COMMIT_OP_FILE
import com.tencent.bkrepo.huggingface.constants.COMMIT_OP_HEADER
import com.tencent.bkrepo.huggingface.constants.COMMIT_OP_LFS
import com.tencent.bkrepo.huggingface.constants.LFS_UPLOAD_MODE
import com.tencent.bkrepo.huggingface.constants.REVISION_MAIN
import com.tencent.bkrepo.huggingface.exception.HfRepoNotFoundException
import com.tencent.bkrepo.huggingface.exception.OperationNotSupportException
import com.tencent.bkrepo.huggingface.exception.RevisionNotFoundException
import com.tencent.bkrepo.huggingface.pojo.CommitRequest
import com.tencent.bkrepo.huggingface.pojo.CommitResponse
import com.tencent.bkrepo.huggingface.pojo.PreUploadInfo
import com.tencent.bkrepo.huggingface.pojo.PreUploadRequest
import com.tencent.bkrepo.huggingface.pojo.PreUploadResponse
import com.tencent.bkrepo.huggingface.pojo.user.CommitDeletedFile
import com.tencent.bkrepo.huggingface.pojo.user.CommitDeletedFolder
import com.tencent.bkrepo.huggingface.pojo.user.CommitFile
import com.tencent.bkrepo.huggingface.pojo.user.CommitHeader
import com.tencent.bkrepo.huggingface.pojo.user.CommitLfsFile
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.eclipse.jgit.ignore.FastIgnoreRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Base64

@Service
class HfUploadService(
    private val repositoryService: RepositoryService,
    private val packageService: PackageService,
    private val nodeService: NodeService,
    private val metadataService: MetadataService,
    private val storageManager: StorageManager,
) : ArtifactService() {

    fun preUpload(request: PreUploadRequest): PreUploadResponse {
        with(request) {
            val repository = checkRepository(projectId, repoName)
            checkPackage(projectId, repoName, type, repoId)
            checkRevision(request, revision)
            val rules = getIgnoreRules(request, repository)
            val preUploadInfos = request.files.map { file ->
                PreUploadInfo(file.path, LFS_UPLOAD_MODE, shouldIgnore(file.path, rules))
            }
            return PreUploadResponse(preUploadInfos)
        }
    }

    fun commit(request: CommitRequest): CommitResponse {
        with(request) {
            checkRepository(projectId, repoName)
            checkPackage(projectId, repoName, type, repoId)
            val sha1 = (SecurityUtils.getUserId() + LocalDateTime.now().toString()).sha1()
            val artifactInfo = HuggingfaceArtifactInfo(
                projectId = projectId,
                repoName = repoName,
                repoId = repoId,
                revision = sha1,
                type = type,
                artifactUri = StringPool.ROOT
            )
            copyBaseRevision(revision, artifactInfo)
            val commitLfsFiles = mutableListOf<CommitLfsFile>()
            val header = castType<CommitHeader>(requests.find { it.key == COMMIT_OP_HEADER }!!.value)
            requests.forEach {
                when (it.key) {
                    COMMIT_OP_FILE -> storeNode(artifactInfo, castType<CommitFile>(it.value))
                    COMMIT_OP_LFS -> {
                        val commitLfsFile = castType<CommitLfsFile>(it.value)
                        moveOrCopyNode(artifactInfo, header, commitLfsFile)
                        commitLfsFiles.add(commitLfsFile)
                    }
                    COMMIT_OP_DEL_FILE -> deleteNode(artifactInfo, castType<CommitDeletedFile>(it.value).path)
                    COMMIT_OP_DEL_FOLDER -> deleteNode(artifactInfo, castType<CommitDeletedFolder>(it.value).path)
                }
            }
            deleteLfsFiles(artifactInfo, commitLfsFiles)
            createPackageVersion(header, sha1)
            return CommitResponse(true, sha1, "$type/$repoId/commit/$sha1")
        }
    }

    private fun checkPackage(projectId: String, repoName: String, type: String, repoId: String): PackageSummary {
        val packageKey = PackageKeys.ofHuggingface(type, repoId)
        return packageService.findPackageByKey(projectId, repoName, packageKey)
            ?: throw HfRepoNotFoundException(repoId)
    }

    private fun CommitRequest.createPackageVersion(
        header: CommitHeader,
        sha1: String
    ) {
        val packageKey = PackageKeys.ofHuggingface(type, repoId)
        val packageVersionCreateRequest = PackageVersionCreateRequest(
            projectId = projectId,
            repoName = repoName,
            packageName = repoId,
            packageKey = packageKey,
            packageType = PackageType.HUGGINGFACE,
            packageDescription = header.summary,
            versionName = sha1,
            size = 0,
            artifactPath = "$repoId/resolve/$sha1",
            createdBy = SecurityUtils.getUserId(),
        )
        packageService.createPackageVersion(packageVersionCreateRequest)
    }

    private fun deleteLfsFiles(artifactInfo: HuggingfaceArtifactInfo, files: List<CommitLfsFile>) {
        files.forEach { file ->
            val nodeDeleteRequest = NodeDeleteRequest(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fullPath = "/${artifactInfo.getRepoId()}/lfs/${file.oid}",
                operator = SecurityUtils.getUserId()
            )
            nodeService.deleteNode(nodeDeleteRequest)
        }
    }

    private inline fun <reified T> castType(value: Any): T {
        return toJson(value).readJsonString()
    }

    private fun checkRepository(projectId: String, repoName: String): RepositoryDetail {
        val repository = repositoryService.getRepoDetail(projectId, repoName)
            ?: throw HfRepoNotFoundException("$projectId/$repoName")
        if (repository.category != RepositoryCategory.LOCAL && repository.category != RepositoryCategory.COMPOSITE) {
            throw OperationNotSupportException()
        }
        return repository
    }

    private fun checkRevision(request: PreUploadRequest, revision: String) {
        if (revision != REVISION_MAIN) {
            logger.warn(
                "package[${request.repoId}] in [${request.projectId}/${request.repoName}] " +
                    "commit from $revision"
            )
            throw OperationNotSupportException()
        }
    }

    private fun deleteNode(artifactInfo: HuggingfaceArtifactInfo, path: String) {
        with (artifactInfo) {
            val fullPath = "/${getRepoId()}/resolve/${getRevision()}/$path"
            nodeService.deleteNode(
                NodeDeleteRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = SecurityUtils.getUserId()
                )
            )
        }
    }

    private fun moveOrCopyNode(
        artifactInfo: HuggingfaceArtifactInfo,
        header: CommitHeader,
        commitLfsFile: CommitLfsFile
    ) {
        with (artifactInfo) {
            if (commitLfsFile.size != null) {
                val nodeMoveRequest = NodeMoveCopyRequest(
                    srcProjectId = projectId,
                    srcRepoName = repoName,
                    srcFullPath = "/${getRepoId()}/lfs/${commitLfsFile.oid}",
                    destFullPath = "/${getRepoId()}/resolve/${getRevision()}/${commitLfsFile.path}",
                    overwrite = true,
                    operator = SecurityUtils.getUserId()
                )
                val nodeDetail = nodeService.copyNode(nodeMoveRequest)
                val metadataSaveRequest = MetadataSaveRequest(
                    projectId = nodeDetail.projectId,
                    repoName = nodeDetail.repoName,
                    fullPath = nodeDetail.fullPath,
                    nodeMetadata = listOf(
                        MetadataModel(CommitHeader::summary.name, header.summary),
                        MetadataModel(CommitHeader::description.name, header.description),
                        MetadataModel(COMMIT_ID_HEADER, artifactInfo.getRevision()!!)
                    )
                )
                metadataService.saveMetadata(metadataSaveRequest)
            } else {
                logger.error(commitLfsFile.toString())
                throw OperationNotSupportException()
            }
        }
    }

    private fun copyBaseRevision(
        baseRevision: String,
        artifactInfo: HuggingfaceArtifactInfo
    ) {
        with(artifactInfo) {
            val packageKey = PackageKeys.ofHuggingface(type.toString(), getRepoId())
            if (baseRevision == REVISION_MAIN) {
                packageService.findPackageByKey(projectId, repoName, packageKey)
                    ?.let { copyBaseRevisionNode(it.latest, artifactInfo) }
            } else {
                packageService.findVersionByName(projectId, repoName, packageKey, baseRevision)
                    ?: throw RevisionNotFoundException(baseRevision)
                copyBaseRevisionNode(baseRevision, artifactInfo)
            }
        }
    }

    private fun copyBaseRevisionNode(baseRevision: String, artifactInfo: HuggingfaceArtifactInfo) {
        if (baseRevision.isEmpty()) {
            return
        }
        val srcFullPath = "${artifactInfo.getRepoId()}/resolve/$baseRevision"
        val dstFullPath = "${artifactInfo.getRepoId()}/resolve/${artifactInfo.getRevision()}"
        val nodeCopyRequest = NodeMoveCopyRequest(
            srcProjectId = artifactInfo.projectId,
            srcRepoName = artifactInfo.repoName,
            srcFullPath = srcFullPath,
            destFullPath = dstFullPath,
            operator = SecurityUtils.getUserId()
        )
        nodeService.copyNode(nodeCopyRequest)
    }

    /**
     * 普通文件上传
     * 使用此方式还是lfs上传取决于preupload接口中返回的uploadMode
     * 即使preupload返回的uploadMode为lfs，客户端仍然以此方式上传空文件
     */
    private fun storeNode(artifactInfo: HuggingfaceArtifactInfo, commitFile: CommitFile) {
        with(artifactInfo) {
            val fullPath = "/${getRepoId()}/resolve/${getRevision()}/${commitFile.path}"
            logger.info("store regular file: $fullPath")
            val decodedContent = when (commitFile.encoding.lowercase()) {
                BASE64_ENCODING -> Base64.getDecoder().decode(commitFile.content)
                else -> {
                    logger.error("unsupported regular file encoding: ${commitFile.encoding}")
                    throw OperationNotSupportException()
                }
            }
            val regularFile = ArtifactFileFactory.build(decodedContent.inputStream())
            val nodeCreateRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = fullPath,
                size = regularFile.getSize(),
                sha256 = regularFile.getFileSha256(),
                md5 = regularFile.getFileMd5(),
                crc64ecma = regularFile.getFileCrc64ecma(),
                operator = SecurityUtils.getUserId(),
                overwrite = true,
            )
            val storageCredentials = ArtifactContextHolder.getRepoDetail()!!.storageCredentials
            storageManager.storeArtifactFile(nodeCreateRequest, regularFile, storageCredentials)
        }
    }

    private fun getIgnoreRules(request: PreUploadRequest, repository: RepositoryDetail): List<String> {
        with(request) {
            val path = "/$repoId/resolve/$revision/"
            val gitignore = nodeService.getNodeDetail(
                ArtifactInfo(
                    projectId,
                    repoName,
                    PathUtils.combineFullPath(path, ".gitignore")
                )
            )
            return storageManager.loadArtifactInputStream(gitignore, repository.storageCredentials)?.readText()?.lines()
                .orEmpty()
        }
    }

    private fun shouldIgnore(path: String, rules: List<String>): Boolean {
        rules.forEach { ignorePath ->
            val rule = FastIgnoreRule(ignorePath)
            if (rule.isMatch(path, false)) {
                return rule.result
            }
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HfUploadService::class.java)
    }
}
