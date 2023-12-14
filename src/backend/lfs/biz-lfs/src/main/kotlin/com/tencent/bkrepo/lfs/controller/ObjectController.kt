/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.lfs.controller

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.lfs.artifact.LfsArtifactInfo
import com.tencent.bkrepo.lfs.pojo.BatchRequest
import com.tencent.bkrepo.lfs.pojo.BatchResponse
import com.tencent.bkrepo.lfs.service.ObjectService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ObjectController(
    private val objectService: ObjectService
) {
    @PostMapping("/{projectId}/{repoName}/objects/batch", "/{projectId}/**/{repoName}/info/lfs/objects/batch")
    fun batch(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody request: BatchRequest
    ): BatchResponse {
        return objectService.batch(projectId, repoName, request)
    }

    @PutMapping("/{projectId}/{repoName}/**")
    fun upload(@ArtifactPathVariable lfsArtifactInfo: LfsArtifactInfo, file: ArtifactFile) {
        objectService.upload(lfsArtifactInfo, file)
    }

    @GetMapping("/{projectId}/{repoName}/**")
    fun download(@ArtifactPathVariable lfsArtifactInfo: LfsArtifactInfo) {
        objectService.download(lfsArtifactInfo)
    }
}
