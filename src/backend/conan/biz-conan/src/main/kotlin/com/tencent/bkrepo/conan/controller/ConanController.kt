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
import com.tencent.bkrepo.common.artifact.audit.NODE_CREATE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_DOWNLOAD_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.conan.constant.CONANFILE
import com.tencent.bkrepo.conan.constant.CONANINFO
import com.tencent.bkrepo.conan.constant.CONAN_MANIFEST
import com.tencent.bkrepo.conan.constant.EXPORT_SOURCES_TGZ_NAME
import com.tencent.bkrepo.conan.constant.PACKAGE_TGZ_NAME
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_CONANFILE_DOWNLOAD_URLS_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_PACKAGE_DOWNLOAD_URLS_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_PACKAGE_MANIFEST_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_PACKAGE_REVISION_FILES_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_PACKAGE_SNAPSHOT_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_PACKAGE_UPLOAD_URLS_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_RECIPE_MANIFEST_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_RECIPE_REVISION_FILES_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_RECIPE_SNAPSHOT_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.GET_RECIPE_UPLOAD_URLS_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.PACKAGE_REVISION_FILE_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.RECIPE_REVISION_FILE_V2
import com.tencent.bkrepo.conan.service.ConanService
import com.tencent.bkrepo.conan.service.ConanUploadDownloadService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * conan相关的请求
 */
@RestController
class ConanController(
    private val conanService: ConanService,
    private val conanUploadDownloadService: ConanUploadDownloadService
) {

    /**
     * 获取制品下CONAN_MANIFEST下载地址
     */
    @GetMapping(GET_RECIPE_MANIFEST_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getRecipeManifest(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(
            conanService.getConanFileDownloadUrls(
                conanArtifactInfo, mutableListOf(CONAN_MANIFEST, EXPORT_SOURCES_TGZ_NAME, CONANFILE)
            )
        )
    }

    /**
     * 获取所有package下CONAN_MANIFEST下载地址
     */
    @GetMapping(GET_PACKAGE_MANIFEST_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getPackageManifest(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(
            conanService.getPackageDownloadUrls(
                conanArtifactInfo, mutableListOf(CONAN_MANIFEST, CONANINFO, PACKAGE_TGZ_NAME)
            )
        )
    }

    /**
     * 获取所有文件以及md5值
     */
    @GetMapping(GET_RECIPE_SNAPSHOT_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getRecipeSnapshot(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getRecipeSnapshot(conanArtifactInfo))
    }

    /**
     * 获取package下所有文件以及md5值
     */
    @GetMapping(GET_PACKAGE_SNAPSHOT_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getPackageSnapshot(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getPackageSnapshot(conanArtifactInfo))
    }

    /**
     * 获取制品下所有文件下载地址
     */
    @GetMapping(GET_CONANFILE_DOWNLOAD_URLS_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getConanFileDownloadurls(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getConanFileDownloadUrls(conanArtifactInfo))
    }

    /**
     * 获取package下所有文件下载地址
     */
    @GetMapping(GET_PACKAGE_DOWNLOAD_URLS_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getPackageDownloadurls(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getPackageDownloadUrls(conanArtifactInfo))
    }

    /**
     * 获取制品下所有文件上传地址
     */
    @PostMapping(GET_RECIPE_UPLOAD_URLS_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun getConanFileUploadurls(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        @RequestBody fileSizes: Map<String, String>
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getConanFileUploadUrls(conanArtifactInfo, fileSizes))
    }

    /**
     * 获取package下所有文件上传地址
     */
    @PostMapping(GET_PACKAGE_UPLOAD_URLS_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun getPackageUploadurls(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        @RequestBody fileSizes: Map<String, String>
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getPackageUploadUrls(conanArtifactInfo, fileSizes))
    }

    // v2
    /**
     * 获取package下所有文件列表
     */
    @GetMapping(GET_PACKAGE_REVISION_FILES_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getPackageRevisionFiles(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getPackageRevisionFiles(conanArtifactInfo))
    }

    /**
     * 获取package下文件
     */
    @AuditEntry(
        actionId = NODE_DOWNLOAD_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DOWNLOAD_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#conanArtifactInfo?.getArtifactFullPath()",
            instanceNames = "#conanArtifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#conanArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#conanArtifactInfo?.repoName")
        ],
        scopeId = "#conanArtifactInfo?.projectId",
        content = ActionAuditContent.NODE_DOWNLOAD_CONTENT
    )
    @GetMapping(PACKAGE_REVISION_FILE_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getPackageRevisionFile(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ) {
        conanUploadDownloadService.downloadFile(conanArtifactInfo)
    }

    /**
     * 上传文件
     */
    @AuditEntry(
        actionId = NODE_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#conanArtifactInfo?.getArtifactFullPath()",
            instanceNames = "#conanArtifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#conanArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#conanArtifactInfo?.repoName")
        ],
        scopeId = "#conanArtifactInfo?.projectId",
        content = ActionAuditContent.NODE_UPLOAD_CONTENT
    )
    @PutMapping(PACKAGE_REVISION_FILE_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun uploadPackageRevisionFile(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        artifactFile: ArtifactFile
    ) {
        conanUploadDownloadService.uploadFile(conanArtifactInfo, artifactFile)
    }

    /**
     * 获取recipe下所有文件列表
     */
    @GetMapping(GET_RECIPE_REVISION_FILES_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getRecipeRevisionFiles(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanService.getRecipeRevisionFiles(conanArtifactInfo))
    }

    /**
     * 获取recipe下文件
     */
    @AuditEntry(
        actionId = NODE_DOWNLOAD_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DOWNLOAD_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#conanArtifactInfo?.getArtifactFullPath()",
            instanceNames = "#conanArtifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#conanArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#conanArtifactInfo?.repoName")
        ],
        scopeId = "#conanArtifactInfo?.projectId",
        content = ActionAuditContent.NODE_DOWNLOAD_CONTENT
    )
    @GetMapping(RECIPE_REVISION_FILE_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun getRecipeRevisionFile(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ) {
        conanUploadDownloadService.downloadFile(conanArtifactInfo)
    }

    /**
     * 上传文件
     */
    @AuditEntry(
        actionId = NODE_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#conanArtifactInfo?.getArtifactFullPath()",
            instanceNames = "#conanArtifactInfo?.getArtifactFullPath()"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#conanArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#conanArtifactInfo?.repoName")
        ],
        scopeId = "#conanArtifactInfo?.projectId",
        content = ActionAuditContent.NODE_UPLOAD_CONTENT
    )
    @PutMapping(RECIPE_REVISION_FILE_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun uploadRecipeRevisionFile(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        artifactFile: ArtifactFile
    ) {
        conanUploadDownloadService.uploadFile(conanArtifactInfo, artifactFile)
    }
}
