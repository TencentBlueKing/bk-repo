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

package com.tencent.bkrepo.conan.controller

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_DOWNLOAD_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.artifact.audit.NODE_WRITE_ACTION
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.UPLOAD_FILE_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.UPLOAD_PACKAGE_FILE_V1
import com.tencent.bkrepo.conan.service.ConanUploadDownloadService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

/**
 * conan上传下载请求
 */
@RestController
class ConanUploadDownloadController(
    private val conanUploadDownloadService: ConanUploadDownloadService
) {
    /**
     * 获取文件
     */
    @AuditEntry(
        actionId = NODE_DOWNLOAD_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DOWNLOAD_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#conanArtifactInfo?.artifactUri",
            instanceNames = "#conanArtifactInfo?.artifactUri"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#conanArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#conanArtifactInfo?.repoName")
        ],
        scopeId = "#conanArtifactInfo?.projectId",
        content = ActionAuditContent.NODE_DOWNLOAD_CONTENT
    )
    @GetMapping(UPLOAD_FILE_V1, UPLOAD_PACKAGE_FILE_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getFile(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ) {
        conanUploadDownloadService.downloadFile(conanArtifactInfo)
    }

    /**
     * 上传文件
     */
    @AuditEntry(
        actionId = NODE_WRITE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_WRITE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#conanArtifactInfo?.artifactUri",
            instanceNames = "#conanArtifactInfo?.artifactUri"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#conanArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#conanArtifactInfo?.repoName")
        ],
        scopeId = "#conanArtifactInfo?.projectId",
        content = ActionAuditContent.NODE_UPLOAD_CONTENT
    )
    @PutMapping(UPLOAD_FILE_V1, UPLOAD_PACKAGE_FILE_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun uploadFile(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        artifactFile: ArtifactFile
    ) {
        conanUploadDownloadService.uploadFile(conanArtifactInfo, artifactFile)
    }
}
