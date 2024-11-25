/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode.PARAMETER_INVALID
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_CREATE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_DOWNLOAD_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.ddc.artifact.ReferenceArtifactInfo
import com.tencent.bkrepo.ddc.artifact.repository.DdcLocalRepository.Companion.HEADER_NAME_HASH
import com.tencent.bkrepo.ddc.component.PermissionHelper
import com.tencent.bkrepo.ddc.pojo.BatchOps
import com.tencent.bkrepo.ddc.pojo.Operation
import com.tencent.bkrepo.ddc.service.ReferenceArtifactService
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_JUPITER_INLINED_PAYLOAD
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_UNREAL_COMPACT_BINARY
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_UNREAL_COMPACT_BINARY_PACKAGE
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/{projectId}/api/v1/refs")
@RestController
class ReferencesController(
    private val referenceArtifactService: ReferenceArtifactService,
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
    @ApiOperation("获取ref")
    @GetMapping(
        "/{repoName}/{$PATH_VARIABLE_BUCKET}/{$PATH_VARIABLE_REF_ID}",
        produces = [
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            MEDIA_TYPE_UNREAL_COMPACT_BINARY,
            MEDIA_TYPE_JUPITER_INLINED_PAYLOAD,
            MEDIA_TYPE_UNREAL_COMPACT_BINARY_PACKAGE
        ]
    )
    fun getRef(
        @ApiParam(value = "ddc ref", required = true)
        @ArtifactPathVariable
        artifactInfo: ReferenceArtifactInfo,
    ) {
        permissionHelper.checkPathPermission(PermissionAction.DOWNLOAD)
        HttpContextHolder.getResponse().contentType = getResponseType(null, MEDIA_TYPE_UNREAL_COMPACT_BINARY)
        referenceArtifactService.downloadRef(artifactInfo)
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
    @ApiOperation("开始创建ref")
    @PutMapping(
        "/{repoName}/{$PATH_VARIABLE_BUCKET}/{$PATH_VARIABLE_REF_ID}",
    )
    fun putObject(
        @ApiParam(value = "ddc ref", required = true)
        @ArtifactPathVariable
        artifactInfo: ReferenceArtifactInfo,
        file: ArtifactFile
    ) {
        permissionHelper.checkPathPermission(PermissionAction.WRITE)
        artifactInfo.inlineBlobHash = HttpContextHolder.getRequest().getHeader(HEADER_NAME_HASH)
            ?: throw BadRequestException(PARAMETER_INVALID, "Missing expected header $HEADER_NAME_HASH")
        referenceArtifactService.createRef(artifactInfo, file)
    }

    @ApiOperation("结束ref创建")
    @PostMapping(
        "/{repoName}/{$PATH_VARIABLE_BUCKET}/{$PATH_VARIABLE_REF_ID}/finalize/{hash}",
    )
    fun finalizeObject(
        @ApiParam(value = "ddc ref", required = true)
        @ArtifactPathVariable
        artifactInfo: ReferenceArtifactInfo,
        @ApiParam("blob hash", required = true)
        @PathVariable hash: String,
    ) {
        permissionHelper.checkPathPermission(PermissionAction.WRITE)
        artifactInfo.inlineBlobHash = hash
        referenceArtifactService.finalize(artifactInfo)
    }

    @ApiOperation("批量读写")
    @PostMapping(
        "/{repoName}",
        consumes = [MEDIA_TYPE_UNREAL_COMPACT_BINARY],
        produces = [MEDIA_TYPE_UNREAL_COMPACT_BINARY],
    )
    fun batchOp(@PathVariable repoName: String) {
        // 检查权限
        val ops = BatchOps.deserialize(HttpContextHolder.getRequest().inputStream.use { it.readBytes() })
        var requiredPermissionAction = PermissionAction.READ
        for (op in ops.ops) {
            if (op.op == Operation.PUT.name) {
                requiredPermissionAction = PermissionAction.WRITE
                break
            }
        }
        permissionHelper.checkPathPermission(requiredPermissionAction)

        // 执行操作
        referenceArtifactService.batch(ops)
    }

    private fun getResponseType(format: String?, default: String): String {
        if (!format.isNullOrEmpty()) {
            return when (format.toLowerCase()) {
                "json" -> MediaType.APPLICATION_JSON_VALUE
                "raw" -> MediaType.APPLICATION_OCTET_STREAM_VALUE
                "uecb" -> MEDIA_TYPE_UNREAL_COMPACT_BINARY
                "uecbpkg" -> MEDIA_TYPE_UNREAL_COMPACT_BINARY_PACKAGE
                else -> throw BadRequestException(
                    PARAMETER_INVALID,
                    "No mapping defined from format $format to mime type"
                )
            }
        }

        val accept = HttpContextHolder.getRequest().getHeaders("Accept").toList()
        if (accept.isEmpty() || accept.contains("*/*")) {
            return default
        }

        accept.firstOrNull { it.toLowerCase() in VALID_CONTENT_TYPES }?.let { return it }

        throw BadRequestException(
            PARAMETER_INVALID,
            "Unable to determine response type for header: $accept"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReferencesController::class.java)

        const val PATH_VARIABLE_BUCKET = ReferenceArtifactInfo.PATH_VARIABLE_BUCKET
        const val PATH_VARIABLE_REF_ID = ReferenceArtifactInfo.PATH_VARIABLE_REF_ID
        val VALID_CONTENT_TYPES = arrayOf(
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MEDIA_TYPE_UNREAL_COMPACT_BINARY,
            MEDIA_TYPE_JUPITER_INLINED_PAYLOAD,
            MEDIA_TYPE_UNREAL_COMPACT_BINARY_PACKAGE
        )
    }
}
