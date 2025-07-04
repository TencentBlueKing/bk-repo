/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.huggingface.artifact.HuggingfaceArtifactInfo
import com.tencent.bkrepo.huggingface.artifact.HuggingfaceRevisionInfo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DownloadController : ArtifactService() {

    @GetMapping("/{projectId}/{repoName}/{organization}/{name}/resolve/{revision}/**",
        "/{projectId}/{repoName}/{type}s/{organization}/{name}/resolve/{revision}/**")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    fun download(@ArtifactPathVariable artifactInfo: HuggingfaceArtifactInfo) {
        val context = ArtifactDownloadContext()
        repository.download(context)
    }

    @GetMapping(
        "/{projectId}/{repoName}/api/{type}s/{organization}/{name}",
        "/{projectId}/{repoName}/api/{type}s/{organization}/{name}/revision/{revision}",
    )
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun info(
        @ArtifactPathVariable artifactInfo: HuggingfaceRevisionInfo,
    ): Any? {
        return repository.query(ArtifactQueryContext())
    }
}
