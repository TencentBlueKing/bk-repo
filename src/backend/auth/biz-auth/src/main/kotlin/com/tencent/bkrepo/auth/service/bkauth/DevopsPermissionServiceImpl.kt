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

package com.tencent.bkrepo.auth.service.bkauth

import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.constant.CUSTOM
import com.tencent.bkrepo.auth.constant.LOG
import com.tencent.bkrepo.auth.constant.PIPELINE
import com.tencent.bkrepo.auth.constant.REPORT
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.MANAGE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.READ
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.WRITE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.VIEW
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.NODE
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.REPO
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.PROJECT
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.repository.AccountRepository
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3PermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3Service
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * 对接devops权限
 */
class DevopsPermissionServiceImpl constructor(
    userRepository: UserRepository,
    roleRepository: RoleRepository,
    accountRepository: AccountRepository,
    permissionRepository: PermissionRepository,
    mongoTemplate: MongoTemplate,
    private val devopsAuthConfig: DevopsAuthConfig,
    private val devopsPipelineService: DevopsPipelineService,
    private val devopsProjectService: DevopsProjectService,
    repositoryClient: RepositoryClient,
    projectClient: ProjectClient,
    bkIamV3Service: BkIamV3Service
) : BkIamV3PermissionServiceImpl(
    userRepository,
    roleRepository,
    accountRepository,
    permissionRepository,
    mongoTemplate,
    bkIamV3Service,
    repositoryClient,
    projectClient
) {
    override fun listPermissionRepo(projectId: String, userId: String, appId: String?): List<String> {
        // 用户为系统管理员，或者当前项目管理员
        if (super.isUserLocalAdmin(userId) || super.isUserLocalProjectAdmin(userId, projectId)) {
            return getAllRepoByProjectId(projectId)
        }

        // devops 体系
        if (checkDevopsProjectPermission(userId, projectId, READ.name)) {
            return getAllRepoByProjectId(projectId)
        }
        return super.listPermissionRepo(projectId, userId, appId)
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {

        // 校验平台账号操作范围
        if (!super.checkPlatformPermission(request)) return false

        // bkiamv3权限校验
        if (super.matchBkiamv3Cond(request)) {
            // 当有v3权限时，返回成功；如没有v3权限则按devops账号体系继续进行判断
            if (super.checkBkIamV3Permission(request)) return true
        }

        return checkDevopsPermission(request)
    }

    override fun listPermissionProject(userId: String): List<String> {
        val localProjectList = super.listPermissionProject(userId)
        val devopsProjectList = devopsProjectService.listProjectByUser(userId)
        if (devopsProjectList.size == 1 && devopsProjectList[0] == "*") {
            return localProjectList
        }
        val allProjectList = localProjectList + devopsProjectList
        return allProjectList.distinct()
    }

    override fun listNoPermissionPath(userId: String, projectId: String, repoName: String): List<String> {
        if (checkDevopsProjectPermission(userId, projectId, MANAGE.name)) {
            return emptyList()
        }
        return super.listNoPermissionPath(userId, projectId, repoName)
    }

    private fun parsePipelineId(path: String): String? {
        val roads = PathUtils.normalizeFullPath(path).split("/")
        return if (roads.size < 2 || roads[1].isBlank()) {
            logger.warn("parse pipelineId failed, path: $path")
            null
        } else {
            roads[1]
        }
    }

    private fun checkDevopsPermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            logger.debug("check devops permission request [$request]")

            if (super.isUserLocalAdmin(uid) || super.isUserLocalProjectAdmin(uid, projectId!!)) {
                return true
            }
            // project权限
            if (resourceType == PROJECT.name) {
                return checkDevopsProjectPermission(uid, projectId!!, action)
                        || super.checkBkIamV3ProjectPermission(projectId!!, uid, action)
            }

            // repo或者node权限
            val pass = when (repoName) {
                CUSTOM, LOG -> {
                    checkDevopsProjectPermission(uid, projectId!!, action)
                }
                PIPELINE -> {
                    checkDevopsPipelineOrProjectPermission(request)
                }
                REPORT -> {
                    checkDevopsReportPermission(action)
                }
                else -> {
                    checkRepoNotInDevops(request)
                }
            }

            if (!pass && matchDevopsCond(request.appId)) {
                logger.warn("devops forbidden [$request]")
            } else {
                logger.debug("devops pass [$request]")
            }
            return pass
        }
    }

    private fun checkRepoNotInDevops(request: CheckPermissionRequest): Boolean {
        with(request) {
            if (resourceType == NODE.name && super.isNodeNeedLocalCheck(projectId!!, repoName!!)) {
                return checkDevopsProjectPermission(uid, projectId!!, action) && super.checkPermission(request)
            } else {
                return super.checkPermission(request) || checkDevopsProjectPermission(uid, projectId!!, action)
            }
        }
    }

    private fun checkDevopsPipelineOrProjectPermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            var projectPass = false
            val pipelinePass = checkDevopsPipelinePermission(uid, projectId!!, path, resourceType, action)
            if (!pipelinePass) {
                logger.warn("devops pipeline permission check fail [$request]")
                projectPass = checkDevopsProjectPermission(uid, projectId!!, action)
                if (projectPass) logger.warn("devops pipeline permission widen to project permission [$request]")
            }
            return pipelinePass || projectPass
        }
    }

    private fun checkDevopsReportPermission(action: String): Boolean {
        return action == READ.name || action == WRITE.name || action == VIEW.name
    }

    private fun checkDevopsPipelinePermission(
        uid: String,
        projectId: String,
        path: String?,
        resourceType: String,
        action: String
    ): Boolean {
        return when (resourceType) {
            REPO.name -> checkDevopsProjectPermission(uid, projectId, action)
            NODE.name -> {
                val pipelineId = parsePipelineId(path ?: return false) ?: return false
                pipelinePermission(uid, projectId, pipelineId, action)
            }
            else -> throw RuntimeException("resource type not supported: $resourceType")
        }
    }

    private fun checkDevopsProjectPermission(userId: String, projectId: String, action: String): Boolean {
        logger.debug("checkDevopsProjectPermission: [$userId,$projectId,$action]")
        return when (action) {
            MANAGE.name -> devopsProjectService.isProjectManager(userId, projectId)
            else -> devopsProjectService.isProjectMember(userId, projectId, action)
        }
    }

    private fun pipelinePermission(userId: String, projectId: String, pipelineId: String, action: String): Boolean {
        logger.debug("pipelinePermission, [$userId,$projectId,$pipelineId,$action]")
        return devopsPipelineService.hasPermission(userId, projectId, pipelineId, action)
    }


    private fun matchDevopsCond(appId: String?): Boolean {
        val devopsAppIdList = devopsAuthConfig.devopsAppIdSet.split(",")
        return devopsAppIdList.contains(appId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DevopsPermissionServiceImpl::class.java)
    }
}
