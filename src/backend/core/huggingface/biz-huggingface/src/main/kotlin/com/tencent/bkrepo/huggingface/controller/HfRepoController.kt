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

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.huggingface.pojo.RepoCreateRequest
import com.tencent.bkrepo.huggingface.pojo.RepoDeleteRequest
import com.tencent.bkrepo.huggingface.pojo.RepoMoveRequest
import com.tencent.bkrepo.huggingface.pojo.RepoUpdateRequest
import com.tencent.bkrepo.huggingface.pojo.RepoUrl
import com.tencent.bkrepo.huggingface.pojo.user.UserRepoCreateRequest
import com.tencent.bkrepo.huggingface.pojo.user.UserRepoDeleteRequest
import com.tencent.bkrepo.huggingface.pojo.user.UserRepoMoveRequest
import com.tencent.bkrepo.huggingface.pojo.user.UserRepoUpdateRequest
import com.tencent.bkrepo.huggingface.service.HfRepoService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * hf中一个模型对应一个仓库
 * bkrepo中一个模型对应一个Package
 */
@RestController
@RequestMapping("/{projectId}/{repoName}")
class HfRepoController(
    private val hfRepoService: HfRepoService,
    private val permissionManager: PermissionManager,
) {

    @PostMapping("/api/repos/create")
    fun create(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody request: UserRepoCreateRequest
    ): RepoUrl {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
        val repoCreateRequest = RepoCreateRequest(
            projectId = projectId,
            repoName = repoName,
            repoId = "${request.organization}/${request.name}",
            private = request.private,
            type = request.type
        )
        return hfRepoService.create(repoCreateRequest)
    }

    @PutMapping("/api/{type}s/{organization}/{name}/settings")
    fun update(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable type: String,
        @PathVariable organization: String,
        @PathVariable name: String,
        @RequestBody request: UserRepoUpdateRequest,
    ) {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
        val repoId = "$organization/$name"
        val repoUpdateRequest = RepoUpdateRequest(
            projectId = projectId,
            repoName = repoName,
            gated = request.gated,
            private = request.private,
            type = type,
            repoId = repoId
        )
        hfRepoService.update(repoUpdateRequest)
    }

    @PostMapping("/api/repos/move")
    fun move(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody request: UserRepoMoveRequest
    ) {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
        val repoMoveRequest = RepoMoveRequest(
            projectId = projectId,
            repoName = repoName,
            fromRepo = request.fromRepo,
            toRepo = request.toRepo,
            type = request.type
        )
        hfRepoService.move(repoMoveRequest)
    }

    @DeleteMapping("/api/repos/delete")
    fun delete(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody request: UserRepoDeleteRequest
    ) {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
        val repoDeleteRequest = RepoDeleteRequest(
            projectId = projectId,
            repoName = repoName,
            repoId = "${request.organization}/${request.name}",
            type = request.type,
            revision = null,
        )
        hfRepoService.delete(repoDeleteRequest)
    }
}
