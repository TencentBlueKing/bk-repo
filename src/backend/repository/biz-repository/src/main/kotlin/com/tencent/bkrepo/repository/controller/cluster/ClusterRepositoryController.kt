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

package com.tencent.bkrepo.repository.controller.cluster

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.security.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.cluster.ClusterRepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import org.springframework.web.bind.annotation.RestController

@RestController
class ClusterRepositoryController(
    private val permissionManager: PermissionManager,
    private val repositoryService: RepositoryService
) : ClusterRepositoryClient {

    override fun getRepoDetail(projectId: String, repoName: String): Response<RepositoryDetail?> {
        permissionManager.checkRepoPermission(PermissionAction.READ, projectId, repoName)
        return ResponseBuilder.success(repositoryService.getRepoDetail(projectId, repoName))
    }

    override fun createRepo(request: RepoCreateRequest): Response<RepositoryDetail> {
        with(request) {
            permissionManager.checkProjectPermission(
                action = if (pluginRequest) PermissionAction.WRITE else PermissionAction.MANAGE,
                projectId = projectId
            )
            return ResponseBuilder.success(repositoryService.createRepo(this))
        }
    }

    override fun updateRepo(request: RepoUpdateRequest): Response<Void> {
        with(request) {
            permissionManager.checkRepoPermission(PermissionAction.MANAGE, projectId, name)
            repositoryService.updateRepo(this)
            return ResponseBuilder.success()
        }
    }

    override fun deleteRepo(request: RepoDeleteRequest): Response<Void> {
        with(request) {
            permissionManager.checkRepoPermission(PermissionAction.DELETE, projectId, name)
            repositoryService.deleteRepo(this)
            return ResponseBuilder.success()
        }
    }
}
