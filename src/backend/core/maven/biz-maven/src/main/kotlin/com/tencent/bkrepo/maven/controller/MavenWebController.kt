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

package com.tencent.bkrepo.maven.controller

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.REPO_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_RESOURCE
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.maven.api.MavenWebResource
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.artifact.MavenDeleteArtifactInfo
import com.tencent.bkrepo.maven.pojo.request.MavenJarSearchRequest
import com.tencent.bkrepo.maven.pojo.response.MavenGAVCResponse
import com.tencent.bkrepo.maven.pojo.response.MavenJarInfoResponse
import com.tencent.bkrepo.maven.service.MavenExtService
import com.tencent.bkrepo.maven.service.MavenService
import org.springframework.web.bind.annotation.RestController

@RestController
class MavenWebController(
    private val mavenService: MavenService,
    private val mavenExtService: MavenExtService
) : MavenWebResource {

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#mavenArtifactInfo?.repoName",
            instanceNames = "#mavenArtifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#mavenArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey")
        ],
        scopeId = "#mavenArtifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_DELETE_CONTENT
    )
    override fun deletePackage(mavenArtifactInfo: MavenDeleteArtifactInfo, packageKey: String): Response<Void> {
        mavenService.delete(mavenArtifactInfo, packageKey, null)
        return ResponseBuilder.success()
    }

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#mavenArtifactInfo?.repoName",
            instanceNames = "#mavenArtifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#mavenArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey"),
            AuditAttribute(name = ActionAuditContent.VERSION_TEMPLATE, value = "#version")

        ],
        scopeId = "#mavenArtifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_VERSION_DELETE_CONTENT
    )
    override fun deleteVersion(
        mavenArtifactInfo: MavenDeleteArtifactInfo,
        packageKey: String,
        version: String?
    ): Response<Void> {
        mavenService.delete(mavenArtifactInfo, packageKey, version)
        return ResponseBuilder.success()
    }

    override fun artifactDetail(
        mavenArtifactInfo: MavenArtifactInfo,
        packageKey: String,
        version: String?
    ): Response<Any?> {
        return ResponseBuilder.success(mavenService.artifactDetail(mavenArtifactInfo, packageKey, version))
    }

    override fun gavc(
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
        g: String?,
        a: String?,
        v: String?,
        c: String?,
        repos: String?
    ): Response<Page<MavenGAVCResponse.UriResult>> {
        return mavenExtService.gavc(projectId, pageNumber, pageSize, g, a, v, c, repos)
    }

    override fun searchJar(request: MavenJarSearchRequest): Response<MavenJarInfoResponse> {
        return ResponseBuilder.success(mavenService.searchJar(request))
    }
}
