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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.artifact.audit.NODE_VIEW_ACTION
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataSaveRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据接口实现类
 */
@Tag(name = "节点元数据用户接口")
@RestController
@RequestMapping("/api/metadata")
class UserMetadataController(
    private val metadataService: MetadataService
) {

    @AuditEntry(
        actionId = NODE_VIEW_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_VIEW_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_METADATA_VIEW_CONTENT
    )
    @Operation(summary = "查询元数据列表")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping(DEFAULT_MAPPING_URI)
    fun listMetadata(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ): Response<Map<String, Any>> {
        artifactInfo.run {
            return ResponseBuilder.success(metadataService.listMetadata(projectId, repoName, getArtifactFullPath()))
        }
    }

    @AuditEntry(
        actionId = NODE_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_METADATA_EDIT_CONTENT
    )
    @Operation(summary = "创建/更新元数据列表")
    @Permission(type = ResourceType.NODE, action = PermissionAction.WRITE)
    @PostMapping(DEFAULT_MAPPING_URI)
    fun save(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody metadataSaveRequest: UserMetadataSaveRequest
    ): Response<Void> {
        artifactInfo.run {
            val request = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                metadata = metadataSaveRequest.metadata,
                nodeMetadata = metadataSaveRequest.nodeMetadata?.map { it.copy(system = false) },
                operator = SecurityUtils.getUserId()
            )
            ActionAuditContext.current().setInstance(request)
            metadataService.saveMetadata(request)
            return ResponseBuilder.success()
        }
    }

    @AuditEntry(
        actionId = NODE_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_METADATA_FORBID_CONTENT
    )
    @Operation(summary = "创建/更新禁用元数据")
    @Permission(type = ResourceType.REPO, action = PermissionAction.UPDATE)
    @PostMapping("/forbid$DEFAULT_MAPPING_URI")
    fun forbidMetadata(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody metadataSaveRequest: UserMetadataSaveRequest
    ): Response<Void> {
        artifactInfo.run {
            val request = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                nodeMetadata = metadataSaveRequest.nodeMetadata
            )
            ActionAuditContext.current().setInstance(request)
            metadataService.addForbidMetadata(request)
            return ResponseBuilder.success()
        }
    }

    @AuditEntry(
        actionId = NODE_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.getArtifactFullPath()",
            instanceNames = "#artifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName"),
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_METADATA_DELETE_CONTENT
    )
    @Operation(summary = "删除元数据")
    @Permission(type = ResourceType.NODE, action = PermissionAction.DELETE)
    @DeleteMapping(DEFAULT_MAPPING_URI)
    fun delete(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody metadataDeleteRequest: UserMetadataDeleteRequest
    ): Response<Void> {
        artifactInfo.run {
            val request = MetadataDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                keyList = metadataDeleteRequest.keyList,
                operator = SecurityUtils.getUserId()
            )
            ActionAuditContext.current().setInstance(request)
            metadataService.deleteMetadata(request, false)
            return ResponseBuilder.success()
        }
    }
}
