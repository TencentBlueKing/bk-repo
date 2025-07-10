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

package com.tencent.bkrepo.generic.controller

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent.NODE_USER_SHARE_DOWNLOAD_URL_CREATE_CONTENT
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.constant.USER_SHARE_CREATE_ACTION
import com.tencent.bkrepo.generic.constant.USER_SHARE_DOWNLOAD_URL_CREATE_ACTION
import com.tencent.bkrepo.generic.pojo.share.UserShareApprovalInfo
import com.tencent.bkrepo.generic.pojo.share.UserShareConfigInfo
import com.tencent.bkrepo.generic.pojo.share.UserShareGenUrlRequest
import com.tencent.bkrepo.generic.pojo.share.UserShareRecordCreateRequest
import com.tencent.bkrepo.generic.pojo.share.UserShareRecordInfo
import com.tencent.bkrepo.generic.pojo.share.UserShareUrls
import com.tencent.bkrepo.generic.service.UserShareApprovalService
import com.tencent.bkrepo.generic.service.UserShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController("GenericUserShareController")
@RequestMapping("/api/user/share")
class UserShareController(
    private val userShareService: UserShareService,
    private val permissionManager: PermissionManager,
    private val userShareApprovalService: UserShareApprovalService
) {

    @AuditEntry(
        actionId = USER_SHARE_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = USER_SHARE_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#request?.path",
            instanceNames = "#request?.path"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#request?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#request?.repoName"),
            AuditAttribute(name = ActionAuditContent.WORKSPACE_NAME_TEMPLATE, value = "#request?.workspaceName"),
        ],
        scopeId = "#request?.projectId",
        content = ActionAuditContent.NODE_USER_SHARE_CREATE_CONTENT
    )
    @PostMapping("/create")
    fun create(
        @RequestBody request: UserShareRecordCreateRequest,
    ): Response<UserShareRecordInfo> {
        permissionManager.checkRepoPermission(PermissionAction.READ, request.projectId, request.repoName)
        return ResponseBuilder.success(userShareService.create(request))
    }

    @GetMapping("/info")
    fun info(
        @RequestParam projectId: String,
        @RequestParam id: String
    ): Response<UserShareRecordInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId)
        return ResponseBuilder.success(userShareService.info(id))
    }

    @PostMapping("/approval/create/{shareId}")
    fun createApproval(
        @PathVariable shareId: String
    ): Response<UserShareApprovalInfo> {
        return ResponseBuilder.success(userShareApprovalService.createApproval(shareId))
    }

    @GetMapping("/approval")
    fun getApproval(
        @RequestParam approvalId: String?,
        @RequestParam shareId: String?,
    ): Response<UserShareApprovalInfo> {
        return ResponseBuilder.success(userShareApprovalService.getApprovalInfo(approvalId, shareId))
    }

    @PostMapping("/approval/callback/{approvalId}")
    fun approvalCallback(
        @PathVariable approvalId: String,
        @RequestHeader(AUTH_HEADER_UID) approveUserId: String
    ) {
        permissionManager.checkPrincipal(SecurityUtils.getUserId(), PrincipalType.ADMIN)
        userShareApprovalService.callback(approvalId, approveUserId)
    }

    @AuditEntry(
        actionId = USER_SHARE_DOWNLOAD_URL_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = USER_SHARE_DOWNLOAD_URL_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
        ),
        content = NODE_USER_SHARE_DOWNLOAD_URL_CREATE_CONTENT
    )
    @PostMapping("/url")
    fun generateUrl(
        @RequestBody request: UserShareGenUrlRequest
    ): Response<UserShareUrls> {
        return ResponseBuilder.success(userShareService.generateUrls(request))
    }

    @GetMapping("/config")
    fun getConfig(
        @RequestParam projectId: String,
        @RequestParam repoName: String
    ): Response<UserShareConfigInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId)
        return ResponseBuilder.success(userShareService.getConfig(projectId, repoName))
    }
}
