/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.cargo.controller.api

import com.google.common.net.HttpHeaders.CONTENT_TYPE
import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.cargo.pojo.CargoSearchResult
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.DOWNLOAD
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.INDEX
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.OWNERS
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.PUBLISH
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.SEARCH
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.UNYANK
import com.tencent.bkrepo.cargo.pojo.artifact.CargoArtifactInfo.Companion.YANK
import com.tencent.bkrepo.cargo.pojo.request.OwnerUserAddRequest
import com.tencent.bkrepo.cargo.service.CargoService
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_CREATE_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.security.permission.Permission
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * https://github.com/rust-lang/cargo/blob/master/src/doc/src/reference/registry-web-api.md
 */
@RestController
class CargoWebController(
    private val cargoService: CargoService,
) {


    @GetMapping(INDEX)
    fun getIndexOfCrate(@ArtifactPathVariable artifactInfo: CargoArtifactInfo) {
        cargoService.getIndexOfCrate(artifactInfo)
    }

    /**
     * cargo upload
     */
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

    @PutMapping(PUBLISH)
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun publish(@ArtifactPathVariable artifactInfo: CargoArtifactInfo, artifactFile: ArtifactFile) {
        cargoService.uploadFile(artifactInfo, artifactFile)
    }

    @GetMapping(DOWNLOAD)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun download(@ArtifactPathVariable artifactInfo: CargoArtifactInfo) {
        cargoService.downloadFile(artifactInfo)
    }


    @DeleteMapping(YANK)
    fun yank(@ArtifactPathVariable artifactInfo: CargoArtifactInfo): ResponseEntity<Any> {
        cargoService.yank(artifactInfo)
        return ResponseEntity.ok().header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("{\"ok\": true}")
    }

    @PutMapping(UNYANK)
    fun unYank(@ArtifactPathVariable artifactInfo: CargoArtifactInfo): ResponseEntity<Any> {
        cargoService.unYank(artifactInfo)
        return ResponseEntity.ok().header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("{\"ok\": true}")
    }


    @GetMapping(OWNERS)
    fun listOwners(
        @ArtifactPathVariable artifactInfo: CargoArtifactInfo,
        @PathVariable("crateName") crateName: String,
    ): ResponseEntity<Any> {
        // TODO 添加的owner才有权限去上传或者yank 对应制品
        return ResponseEntity.ok("")

    }

    @PutMapping(OWNERS)
    fun addOwners(
        @ArtifactPathVariable artifactInfo: CargoArtifactInfo,
        @RequestBody users: OwnerUserAddRequest,
    ): ResponseEntity<Any> {
        return ResponseEntity.ok("")

    }

    @DeleteMapping(OWNERS)
    fun removeOwners(
        @ArtifactPathVariable artifactInfo: CargoArtifactInfo,
        @RequestBody users: OwnerUserAddRequest,
    ): ResponseEntity<Any> {
        return ResponseEntity.ok("")

    }

    @GetMapping(SEARCH)
    fun search(
        @ArtifactPathVariable artifactInfo: CargoArtifactInfo,
        @RequestParam q: String,
        @RequestParam("per_page") perPage: Int = 10
    ): ResponseEntity<CargoSearchResult> {
        return ResponseEntity.ok(cargoService.search(artifactInfo, q, perPage))
    }
}