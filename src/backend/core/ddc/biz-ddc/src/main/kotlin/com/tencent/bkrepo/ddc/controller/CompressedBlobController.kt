/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.ddc.controller

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_CREATE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_DOWNLOAD_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.ddc.artifact.CompressedBlobArtifactInfo
import com.tencent.bkrepo.ddc.component.PermissionHelper
import com.tencent.bkrepo.ddc.service.CompressedBlobService
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/{projectId}/api/v1/compressed-blobs")
@RestController
class CompressedBlobController(
    private val compressedBlobService: CompressedBlobService,
    private val permissionHelper: PermissionHelper,
) {

    @AuditEntry(
        actionId = NODE_DOWNLOAD_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DOWNLOAD_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_DOWNLOAD_CONTENT
    )
    @Operation(summary = "获取压缩后的缓存")
    @GetMapping(
        "/{repoName}/{$PATH_VARIABLE_CONTENT_ID}",
        produces = [MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER, MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    fun get(
        @Parameter(name = "ddc compressed blob", required = true)
        @ArtifactPathVariable
        artifactInfo: CompressedBlobArtifactInfo,
    ) {
        permissionHelper.checkPathPermission(PermissionAction.DOWNLOAD)
        compressedBlobService.get(artifactInfo)
    }

    @AuditEntry(
        actionId = NODE_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_UPLOAD_CONTENT
    )
    @Operation(summary = "上传压缩后的缓存")
    @PutMapping("/{repoName}/{$PATH_VARIABLE_CONTENT_ID}", consumes = [MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER])
    fun put(
        @Parameter(name = "ddc compressed blob", required = true)
        @ArtifactPathVariable
        artifactInfo: CompressedBlobArtifactInfo,
        artifactFile: ArtifactFile
    ) {
        permissionHelper.checkPathPermission(PermissionAction.WRITE)
        compressedBlobService.put(artifactInfo, artifactFile)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CompressedBlobController::class.java)
        const val PATH_VARIABLE_CONTENT_ID = CompressedBlobArtifactInfo.PATH_VARIABLE_CONTENT_ID
    }
}
