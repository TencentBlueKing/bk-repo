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
import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_DELETE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_ALL_PACKAGE_UNDER_REVISION_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_FILES_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_PACKAGES_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_PACKAGE_RECIPE_REVISION_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_PACKAGE_REVISION_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_RECIPE_REVISIONS_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_RECIPE_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REMOVE_RECIPE_V2
import com.tencent.bkrepo.conan.pojo.request.FileRemoveRequest
import com.tencent.bkrepo.conan.pojo.request.PackageIdRemoveRequest
import com.tencent.bkrepo.conan.service.ConanDeleteService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * conan删除相关的请求
 */
@RestController
class ConanDeleteController(
    private val conanDeleteService: ConanDeleteService
) {

    /**
     * Remove any existing recipes or its packages created.
     * Will remove all revisions, packages and package revisions (parent folder)
     */
    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
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
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @DeleteMapping(REMOVE_RECIPE_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun removeRecipe(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        conanDeleteService.removeConanFile(conanArtifactInfo)
        return ConanCommonController.buildResponse(StringPool.EMPTY)
    }

    /**
     * if packageIds is empty, then will remove all packages
     */
    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
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
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @PostMapping(REMOVE_PACKAGES_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun removePackages(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        @RequestBody removeRequest: PackageIdRemoveRequest
    ): ResponseEntity<Any> {
        ActionAuditContext.current().setInstance(removeRequest)
        conanDeleteService.removePackages(conanArtifactInfo, DEFAULT_REVISION_V1, removeRequest.packageIds)
        return ConanCommonController.buildResponse(StringPool.EMPTY)
    }

    /**
     * The remove files is a part of the upload process,
     * where the revision in v1 will always be DEFAULT_REVISION_V1
     */
    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
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
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @PostMapping(REMOVE_FILES_V1)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun removeRecipeFiles(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        @RequestBody fileRemoveRequest: FileRemoveRequest
    ): ResponseEntity<Any> {
        ActionAuditContext.current().setInstance(fileRemoveRequest)
        conanDeleteService.removeRecipeFiles(conanArtifactInfo, fileRemoveRequest.files)
        return ConanCommonController.buildResponse(StringPool.EMPTY)
    }

    /**
     * Remove any existing recipes or its packages created.
     * Will remove all revisions, packages and package revisions (parent folder)
     */
    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
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
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @DeleteMapping(REMOVE_RECIPE_V2, REMOVE_RECIPE_REVISIONS_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun removeRecipeV2(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        conanDeleteService.removeConanFile(conanArtifactInfo)
        return ConanCommonController.buildResponse(StringPool.EMPTY)
    }

    /**
     * - If both RRev and PRev are specified, it will remove the specific package revision
     * of the specific recipe revision.
     * - If PRev is NOT specified but RRev is specified (package_recipe_revision_url)
     * it will remove all the package revisions
     */
    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
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
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @DeleteMapping(REMOVE_PACKAGE_RECIPE_REVISION_V2, REMOVE_PACKAGE_REVISION_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun removePackagesV2(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        conanDeleteService.removePackage(conanArtifactInfo)
        return ConanCommonController.buildResponse(StringPool.EMPTY)
    }

    /**
     * Remove all packages from a RREV
     */
    @AuditEntry(
        actionId = NODE_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DELETE_ACTION,
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
        content = ActionAuditContent.NODE_DELETE_CONTENT
    )
    @DeleteMapping(REMOVE_ALL_PACKAGE_UNDER_REVISION_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun removeAllPackagesV2(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo
    ): ResponseEntity<Any> {
        conanDeleteService.removePackages(conanArtifactInfo, conanArtifactInfo.revision!!)
        return ConanCommonController.buildResponse(StringPool.EMPTY)
    }
}
