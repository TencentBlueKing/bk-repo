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

package com.tencent.bkrepo.generic.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.util.PipelineRepoUtils
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.BATCH_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.BLOCK_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.GENERIC_MAPPING_URI
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_ID
import com.tencent.bkrepo.generic.pojo.BatchDownloadPaths
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.CompressedFileInfo
import com.tencent.bkrepo.generic.pojo.UploadTransactionInfo
import com.tencent.bkrepo.generic.service.CompressedFileService
import com.tencent.bkrepo.generic.service.DownloadService
import com.tencent.bkrepo.generic.service.UploadService
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GenericController(
    private val uploadService: UploadService,
    private val downloadService: DownloadService,
    private val permissionManager: PermissionManager,
    private val compressedFileService: CompressedFileService,
) {

    @PutMapping(GENERIC_MAPPING_URI)
    @Permission(ResourceType.NODE, PermissionAction.WRITE)
    fun upload(@ArtifactPathVariable artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        uploadService.upload(artifactInfo, file)
    }

    @Permission(ResourceType.NODE, PermissionAction.DELETE)
    @DeleteMapping(GENERIC_MAPPING_URI)
    fun delete(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
    ): Response<Void> {
        uploadService.delete(userId, artifactInfo)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.NODE, PermissionAction.READ)
    @GetMapping(GENERIC_MAPPING_URI)
    fun download(@ArtifactPathVariable artifactInfo: GenericArtifactInfo) {
        downloadService.download(artifactInfo)
    }

    @Permission(ResourceType.NODE, PermissionAction.WRITE)
    @PostMapping(BLOCK_MAPPING_URI)
    fun startBlockUpload(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
    ): Response<UploadTransactionInfo> {
        return ResponseBuilder.success(uploadService.startBlockUpload(userId, artifactInfo))
    }

    @Permission(ResourceType.NODE, PermissionAction.WRITE)
    @DeleteMapping(BLOCK_MAPPING_URI)
    fun abortBlockUpload(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
    ): Response<Void> {
        uploadService.abortBlockUpload(userId, uploadId, artifactInfo)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.NODE, PermissionAction.WRITE)
    @PutMapping(BLOCK_MAPPING_URI)
    fun completeBlockUpload(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
    ): Response<Void> {
        uploadService.completeBlockUpload(userId, uploadId, artifactInfo)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @GetMapping(BLOCK_MAPPING_URI)
    fun listBlock(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
    ): Response<List<BlockInfo>> {
        return ResponseBuilder.success(uploadService.listBlock(userId, uploadId, artifactInfo))
    }

    @RequestMapping(BATCH_MAPPING_URI, method = [RequestMethod.GET, RequestMethod.POST])
    fun batchDownload(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody batchDownloadPaths: BatchDownloadPaths,
    ) {
        PipelineRepoUtils.forbidPipeline(repoName)
        Preconditions.checkNotBlank(batchDownloadPaths.paths, BatchDownloadPaths::paths.name)
        val artifacts = batchDownloadPaths.paths.map { GenericArtifactInfo(projectId, repoName, it) }
            .distinctBy { it.getArtifactFullPath() }
        permissionManager.checkNodePermission(
            action = PermissionAction.READ,
            projectId = projectId,
            repoName = repoName,
            path = *artifacts.map { it.getArtifactFullPath() }.toTypedArray(),
        )
        downloadService.batchDownload(artifacts)
    }

    @Permission(ResourceType.NODE, PermissionAction.READ)
    @GetMapping("/compressed/list/$GENERIC_MAPPING_URI")
    fun listCompressedFile(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
    ): Response<List<CompressedFileInfo>> {
        return ResponseBuilder.success(compressedFileService.listCompressedFile(artifactInfo))
    }

    @Permission(ResourceType.NODE, PermissionAction.READ)
    @GetMapping("/compressed/preview/$GENERIC_MAPPING_URI")
    fun previewCompressedFile(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam filePath: String,
    ) {
        compressedFileService.previewCompressedFile(artifactInfo, filePath)
    }

    @Permission(ResourceType.NODE, PermissionAction.READ)
    @GetMapping("/allow/download/$GENERIC_MAPPING_URI")
    fun allowDownload(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam ip: String,
        @RequestParam fromApp: Boolean
    ): Response<Boolean> {
        return ResponseBuilder.success(downloadService.allowDownload(artifactInfo, ip, fromApp))
    }

    @ApiOperation("根据路径查看节点详情")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping("/detail/${DefaultArtifactInfo.DEFAULT_MAPPING_URI}")
    fun query(@ArtifactPathVariable artifactInfo: GenericArtifactInfo): Response<Any> {
        val node = downloadService.query(artifactInfo)
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, artifactInfo.getArtifactFullPath())
        return ResponseBuilder.success(node)
    }


    @ApiOperation("自定义查询节点")
    @PostMapping("/{projectId}/{repoName}/search")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun search(
        @PathVariable("projectId") projectId: String,
        @PathVariable("repoName") repoName: String,
        @RequestBody queryModel: QueryModel
    ): Response<Page<Any>> {
        // 设置artifact，避免创建context失败
        HttpContextHolder.getRequest().setAttribute(ARTIFACT_INFO_KEY, ArtifactInfo(projectId, repoName, ""))
        val pageRequest = Pages.ofRequest(queryModel.page.pageNumber, queryModel.page.pageSize)
        val page = Pages.ofResponse(pageRequest, 0L, downloadService.search(queryModel))
        return ResponseBuilder.success(page)
    }
}
