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

package com.tencent.bkrepo.oci.controller.user

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_LINK
import com.tencent.bkrepo.oci.constant.OCI_PROJECT_ID
import com.tencent.bkrepo.oci.constant.OCI_REPO_NAME
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo.Companion.DOCKER_CATALOG_SUFFIX
import com.tencent.bkrepo.oci.pojo.artifact.OciTagArtifactInfo
import com.tencent.bkrepo.oci.service.OciCatalogService
import io.swagger.annotations.ApiParam
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CatalogController(private val catalogService: OciCatalogService) {

    /**
     * 返回仓库下所有image名列表
     */
    @GetMapping("/{projectId}/{repoName}$DOCKER_CATALOG_SUFFIX")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun list(
        artifactInfo: OciTagArtifactInfo,
        @PathVariable
        @ApiParam(value = OCI_PROJECT_ID, required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = OCI_REPO_NAME, required = true)
        repoName: String,
        @RequestParam(required = false)
        @ApiParam(value = "n", required = false)
        n: Int?,
        @RequestParam(required = false)
        @ApiParam(value = "last", required = false)
        last: String?
    ): ResponseEntity<Any> {

        val catalogResponse = catalogService.getCatalog(
            artifactInfo = artifactInfo,
            n = n,
            last = last
        )
        val httpHeaders = HttpHeaders()
        httpHeaders.set(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
        val left = catalogResponse.left
        if (left > 0) {
            val lastTag = catalogResponse.repositories.last()
            httpHeaders.set(DOCKER_LINK, "</v2/_catalog?last=$lastTag&n=$left>; rel=\"next\"")
        }
        return ResponseEntity(catalogResponse, httpHeaders, HttpStatus.OK)
    }
}
