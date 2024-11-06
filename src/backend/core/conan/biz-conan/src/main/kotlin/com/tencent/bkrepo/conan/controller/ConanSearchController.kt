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

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.PACKAGE_SEARCH_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.PACKAGE_SEARCH_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.REVISION_SEARCH_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.SEARCH_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.SEARCH_V2
import com.tencent.bkrepo.conan.service.ConanSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * conan package搜索请求
 */
@RestController
class ConanSearchController(
    private val conanSearchService: ConanSearchService
) {
    @GetMapping(SEARCH_V1, SEARCH_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun search(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam q: String?,
        @RequestParam ignorecase: Boolean?
    ): ResponseEntity<Any> {
        val ignoreCase = ignorecase ?: true
        return ConanCommonController.buildResponse(conanSearchService.search(projectId, repoName, q, ignoreCase))
    }

    @GetMapping(PACKAGE_SEARCH_V1, PACKAGE_SEARCH_V2, REVISION_SEARCH_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun searchPackages(
        @ArtifactPathVariable conanArtifactInfo: ConanArtifactInfo,
        @RequestParam q: String?
    ): ResponseEntity<Any> {
        return ConanCommonController.buildResponse(conanSearchService.searchPackages(q, conanArtifactInfo))
    }
}
