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

package com.tencent.bkrepo.helm.controller.api

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_DOWNLOAD_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo.Companion.HELM_INDEX_YAML_URL
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo.Companion.HELM_INSTALL_URL
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo.Companion.HELM_PROV_INSTALL_URL
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Suppress("MVCPathVariableInspection")
@RestController
class ChartRepositoryController(
    private val chartRepositoryService: ChartRepositoryService,
) {
    @GetMapping(HelmArtifactInfo.CHARTS_LIST, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun allChartsList(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo,
        @RequestParam startTime: LocalDateTime?
    ): ResponseEntity<Any> {
        return chartRepositoryService.allChartsList(artifactInfo, startTime)
    }

    /**
     * query index.yaml
     */
    @GetMapping(HELM_INDEX_YAML_URL)
    fun queryIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo) {
        chartRepositoryService.queryIndexYaml(artifactInfo)
    }

    /**
     * retrieved when you run helm install chartmuseum/mychart
     */
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
    @GetMapping(HELM_INSTALL_URL)
    fun installTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo) {
        chartRepositoryService.installTgz(artifactInfo)
    }

    /**
     * retrieved when you run helm install with the --verify flag
     */
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
    @GetMapping(HELM_PROV_INSTALL_URL)
    fun installProv(@ArtifactPathVariable artifactInfo: HelmArtifactInfo) {
        chartRepositoryService.installProv(artifactInfo)
    }

    /**
     * regenerate index.yaml
     */
    @PostMapping("/{projectId}/{repoName}/regenerate")
    fun regenerateIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo): Response<Void> {
        chartRepositoryService.regenerateIndexYaml(artifactInfo, true)
        return ResponseBuilder.success()
    }

    /**
     * regenerate index.yaml
     */
    @PostMapping("/{projectId}/{repoName}/regenerate/v2")
    fun regenerateIndexYamlV2(@ArtifactPathVariable artifactInfo: HelmArtifactInfo): Response<Void> {
        chartRepositoryService.regenerateIndexYaml(artifactInfo, false)
        return ResponseBuilder.success()
    }

    /**
     * refresh index.yaml and package info for remote
     */
    @PostMapping("/{projectId}/{repoName}/refresh")
    fun refreshIndexYamlAndPackage(@ArtifactPathVariable artifactInfo: HelmArtifactInfo): Response<Void> {
        chartRepositoryService.updatePackageForRemote(artifactInfo)
        return ResponseBuilder.success()
    }

    /**
     * batch install chart
     */
    @GetMapping("/{projectId}/{repoName}/batch/charts")
    fun batchInstallTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo, @RequestParam startTime: LocalDateTime) {
        chartRepositoryService.batchInstallTgz(artifactInfo, startTime)
    }
}
