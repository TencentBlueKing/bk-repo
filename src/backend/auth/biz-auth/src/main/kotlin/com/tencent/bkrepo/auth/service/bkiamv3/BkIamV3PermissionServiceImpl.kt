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

package com.tencent.bkrepo.auth.service.bkiamv3

import com.tencent.bkrepo.auth.config.BkAuthConfig
import com.tencent.bkrepo.auth.pojo.enums.ActionTypeMapping
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.repository.AccountRepository
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.bkauth.BkAuthPermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkauth.BkAuthPipelineService
import com.tencent.bkrepo.auth.service.bkauth.BkAuthProjectService
import com.tencent.bkrepo.auth.util.BkIamV3Utils.convertActionType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * 对接蓝鲸权限中心V3 RBAC
 */
class BkIamV3PermissionServiceImpl(
    userRepository: UserRepository,
    roleRepository: RoleRepository,
    accountRepository: AccountRepository,
    permissionRepository: PermissionRepository,
    mongoTemplate: MongoTemplate,
    private val bkiamV3Service: BkIamV3Service,
    repositoryClient: RepositoryClient,
    projectClient: ProjectClient,
    bkAuthConfig: BkAuthConfig,
    bkAuthPipelineService: BkAuthPipelineService,
    bkAuthProjectService: BkAuthProjectService
) : BkAuthPermissionServiceImpl(
    userRepository,
    roleRepository,
    accountRepository,
    permissionRepository,
    mongoTemplate,
    bkAuthConfig,
    bkAuthPipelineService,
    bkAuthProjectService,
    repositoryClient,
    projectClient
) {
    override fun checkPermission(request: CheckPermissionRequest): Boolean {
        logger.debug("checkPermission, request: $request")
        return super.checkPermission(request) || checkBkIamV3Permission(request)
    }

    override fun listPermissionRepo(projectId: String, userId: String, appId: String?): List<String> {
        logger.debug("listPermissionRepo, projectId: $projectId, userId: $userId, appId: $appId")
        return mergeResult(
            super.listPermissionRepo(projectId, userId, appId),
            listV3PermissionRepo(projectId, userId)
        )
    }

    override fun listPermissionProject(userId: String): List<String> {
        logger.debug("listPermissionProject, userId: $userId")
        return mergeResult(
            super.listPermissionProject(userId),
            listV3PermissionProject(userId)
        )
    }

    private fun listV3PermissionRepo(projectId: String, userId: String) : List<String> {
        val pList = bkiamV3Service.listPermissionResources(
            userId = userId,
            projectId = projectId,
            resourceType = ResourceType.REPO.id(),
            action = ActionTypeMapping.REPO_VIEW.id()
        )
        return if (pList.contains(StringPool.POUND)) {
            repoClient.listRepo(projectId).data?.map { it.name } ?: emptyList()
        } else {
            pList
        }
    }

    private fun listV3PermissionProject(userId: String) : List<String> {
        val pList = bkiamV3Service.listPermissionResources(
            userId = userId,
            resourceType = ResourceType.PROJECT.id(),
            action = ActionTypeMapping.PROJECT_VIEW.id()
        )
        return if (pList.contains(StringPool.POUND)) {
            projectClient.listProject().data?.map { it.name } ?: emptyList()
        } else {
            pList
        }
    }

    private fun mergeResult(list: List<String>, v3list: List<String>) : List<String> {
        val set = mutableSetOf<String>()
        set.addAll(list)
        set.addAll(v3list)
        return set.toList()
    }

    private fun checkBkIamV3Permission(request: CheckPermissionRequest): Boolean {
        val resourceId = bkiamV3Service.getResourceId(
            request.resourceType, request.projectId, request.repoName, request.path
        ) ?: StringPool.EMPTY
        return bkiamV3Service.validateResourcePermission(
            userId = request.uid,
            projectId = request.projectId!!,
            repoName = request.repoName,
            resourceType = request.resourceType.toLowerCase(),
            action = convertActionType(request.resourceType, request.action),
            resourceId = resourceId,
            appId = request.appId
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkIamV3PermissionServiceImpl::class.java)
    }
}

