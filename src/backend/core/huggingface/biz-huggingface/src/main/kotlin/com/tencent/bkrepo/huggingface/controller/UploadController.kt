/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.huggingface.pojo.CommitRequest
import com.tencent.bkrepo.huggingface.pojo.CommitResponse
import com.tencent.bkrepo.huggingface.pojo.PreUploadRequest
import com.tencent.bkrepo.huggingface.pojo.PreUploadResponse
import com.tencent.bkrepo.huggingface.pojo.ValidateYamlRequest
import com.tencent.bkrepo.huggingface.pojo.ValidateYamlResponse
import com.tencent.bkrepo.huggingface.pojo.user.UserCommitRequest
import com.tencent.bkrepo.huggingface.pojo.user.UserPreUploadRequest
import com.tencent.bkrepo.huggingface.service.UploadService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UploadController(private val uploadService: UploadService) {

    @PostMapping("{projectId}/{repoName}/api/{type}s/{organization}/{name}/preupload/{revision}")
    fun preUpload(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable type: String,
        @PathVariable organization: String,
        @PathVariable name: String,
        @PathVariable revision: String,
        @RequestBody request: UserPreUploadRequest
    ): PreUploadResponse {
        val repoId = "$organization/$name"
        val preUploadRequest = PreUploadRequest(
            projectId = projectId,
            repoName = repoName,
            type = type,
            revision = revision,
            repoId = repoId,
            files = request.files
        )
        return uploadService.preUpload(preUploadRequest)
    }

    @PostMapping("/{projectId}/{repoName}/api/validate-yaml")
    fun validateYaml(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody request: ValidateYamlRequest
    ): ValidateYamlResponse {
        // TODO 实现README文件校验
        return ValidateYamlResponse(emptyList(), emptyList())
    }


    @PostMapping("/{projectId}/{repoName}/api/{type}s/{organization}/{name}/commit/{revision}")
    fun commit(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable type: String,
        @PathVariable organization: String,
        @PathVariable name: String,
        @PathVariable revision: String,
        @RequestBody requests: List<UserCommitRequest>
    ): CommitResponse {
        val commitRequest = CommitRequest(
            projectId = projectId,
            repoName = repoName,
            type = type,
            revision = revision,
            repoId = "$organization/$name",
            requests = requests
        )
        return uploadService.commit(commitRequest)
    }
}
