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

package com.tencent.bkrepo.pypi.controller

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
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.service.PypiWebService
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "pypi产品接口")
@RestController
@RequestMapping("/ext")
class PypiWebResourceController(
    private val pypiWebService: PypiWebService
) {

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#pypiArtifactInfo?.repoName",
            instanceNames = "#pypiArtifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#pypiArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey")
        ],
        scopeId = "#pypiArtifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_DELETE_CONTENT
    )
    @Operation(summary = "pypi包删除接口")
    @DeleteMapping(PypiArtifactInfo.PYPI_EXT_PACKAGE_DELETE)
    fun deletePackage(pypiArtifactInfo: PypiArtifactInfo, packageKey: String): Response<Void> {
        pypiWebService.deletePackage(pypiArtifactInfo, packageKey)
        return ResponseBuilder.success()
    }

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#pypiArtifactInfo?.repoName",
            instanceNames = "#pypiArtifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#pypiArtifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey"),
            AuditAttribute(name = ActionAuditContent.VERSION_TEMPLATE, value = "#version")

        ],
        scopeId = "#pypiArtifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_VERSION_DELETE_CONTENT
    )
    @Operation(summary = "pypi版本删除接口")
    @DeleteMapping(PypiArtifactInfo.PYPI_EXT_VERSION_DELETE)
    fun deleteVersion(
        pypiArtifactInfo: PypiArtifactInfo,
        packageKey: String,
        version: String?,
        contentPath: String?
    ): Response<Void> {
        pypiWebService.delete(pypiArtifactInfo, packageKey, version, contentPath)
        return ResponseBuilder.success()
    }

    @Operation(summary = "pypi版本详情接口")
    @GetMapping(PypiArtifactInfo.PYPI_EXT_DETAIL)
    fun artifactDetail(
        pypiArtifactInfo: PypiArtifactInfo,
        packageKey: String,
        version: String?
    ): Response<Any?> {
        return ResponseBuilder.success(pypiWebService.artifactDetail(pypiArtifactInfo, packageKey, version))
    }

    @Operation(summary = "pypi包版本列表接口")
    @GetMapping(PypiArtifactInfo.PYPI_EXT_PACKAGE_LIST)
    fun versionListPage(
        pypiArtifactInfo: PypiArtifactInfo,
        packageKey: String,
        pageNumber: Int,
        pageSize: Int
    ): Response<Page<PackageVersion>> {
        return ResponseBuilder.success(
            pypiWebService.versionListPage(
                pypiArtifactInfo,
                packageKey,
                pageNumber,
                pageSize
            )
        )
    }
}
