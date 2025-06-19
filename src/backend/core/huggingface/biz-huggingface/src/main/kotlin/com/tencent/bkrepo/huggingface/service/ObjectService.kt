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

package com.tencent.bkrepo.huggingface.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.huggingface.artifact.HuggingfaceArtifactInfo
import com.tencent.bkrepo.huggingface.config.HuggingfaceProperties
import com.tencent.bkrepo.huggingface.exception.HfRepoNotFoundException
import com.tencent.bkrepo.lfs.constant.BASIC_TRANSFER
import com.tencent.bkrepo.lfs.constant.UPLOAD_OPERATION
import com.tencent.bkrepo.lfs.pojo.ActionDetail
import com.tencent.bkrepo.lfs.pojo.BatchRequest
import com.tencent.bkrepo.lfs.pojo.BatchResponse
import com.tencent.bkrepo.lfs.pojo.LfsObject
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.stereotype.Service

@Service
class ObjectService(
    private val repositoryService: RepositoryService,
    private val permissionManager: PermissionManager,
    private val huggingfaceProperties: HuggingfaceProperties,
    private val authenticationManager: AuthenticationManager
) : ArtifactService() {

    /**
     * [batch api实现](https://github.com/git-lfs/git-lfs/blob/main/docs/api/batch.md)
     */
    fun batch(
        projectId: String,
        repoName: String,
        organization: String,
        name: String,
        request: BatchRequest
    ): BatchResponse {
        Preconditions.checkArgument(request.transfers.contains(BASIC_TRANSFER), BatchRequest::transfers.name)
        val repo = getRepoDetail(projectId, repoName)
        if (request.operation != UPLOAD_OPERATION) {
            throw UnsupportedOperationException()
        }
        if (repo.category == RepositoryCategory.REMOTE || repo.category == RepositoryCategory.PROXY) {
            throw UnsupportedOperationException()
        }
        val objects = buildLfsObjects(request, repo, organization, name)
        return BatchResponse(BASIC_TRANSFER, objects)
    }

    fun upload(huggingfaceArtifactInfo: HuggingfaceArtifactInfo, file: ArtifactFile, token: String) {
        val userInfo = authenticationManager.findUserByToken(token)
        with(huggingfaceArtifactInfo) {
            permissionManager.checkNodePermission(
                PermissionAction.WRITE,
                projectId,
                repoName,
                getArtifactFullPath(),
                userId = userInfo.userId
            )
        }
        HttpContextHolder.getRequest().setAttribute(USER_KEY, userInfo.userId)
        val context = ArtifactUploadContext(file)
        repository.upload(context)
    }

    private fun buildLfsObjects(
        request: BatchRequest,
        repo: RepositoryDetail,
        organization: String,
        name: String
    ): List<LfsObject> {
        val token = HeaderUtils.getHeader(HttpHeaders.AUTHORIZATION)?.removePrefix(BEARER_AUTH_PREFIX).toString()
        return request.objects.map {
            it.copy(
                authenticated = true,
                actions = mapOf(
                    request.operation to ActionDetail(
                        href = huggingfaceProperties.domain.removeSuffix(StringPool.SLASH) +
                            "/lfs/${repo.projectId}/${repo.name}" +
                            "/$organization/$name/lfs/${it.oid}" +
                            "?size=${it.size}&ref=${request.ref["name"]}&token=$token",
                        header = mapOf(
                            HttpHeaders.AUTHORIZATION to BEARER_AUTH_PREFIX + token
                        ).toMutableMap(),
                        expiresIn = TOKEN_EXPIRES_SECONDS
                    )
                )
            )
        }
    }

    private fun getRepoDetail(projectId: String, repoName: String): RepositoryDetail {
        return repositoryService.getRepoDetail(projectId, repoName)
            ?: throw HfRepoNotFoundException("$projectId/$repoName")
    }

    companion object {
        private const val TOKEN_EXPIRES_SECONDS = 86400L
    }
}
