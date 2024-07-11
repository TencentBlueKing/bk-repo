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

package com.tencent.bkrepo.auth.service.bkdevops

import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.AccountDao
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.constant.CUSTOM
import com.tencent.bkrepo.auth.constant.LOG
import com.tencent.bkrepo.auth.constant.PIPELINE
import com.tencent.bkrepo.auth.constant.REPORT
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.RepoAuthConfigDao
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.VIEW
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.WRITE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.MANAGE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.READ
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.NODE
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.REPO
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.PROJECT
import com.tencent.bkrepo.auth.pojo.role.ExternalRoleResult
import com.tencent.bkrepo.auth.pojo.role.RoleSource
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3PermissionServiceImpl
import com.tencent.bkrepo.auth.service.bkiamv3.BkIamV3Service
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory

/**
 * 对接devops权限
 */
class DevopsPermissionServiceImpl constructor(
    roleRepository: RoleRepository,
    accountDao: AccountDao,
    permissionDao: PermissionDao,
    userDao: UserDao,
    personalPathDao: PersonalPathDao,
    repoAuthConfigDao: RepoAuthConfigDao,
    private val devopsAuthConfig: DevopsAuthConfig,
    private val devopsPipelineService: DevopsPipelineService,
    private val devopsProjectService: DevopsProjectService,
    repoClient: RepositoryClient,
    projectClient: ProjectClient,
    bkIamV3Service: BkIamV3Service
) : BkIamV3PermissionServiceImpl(
    bkIamV3Service,
    userDao,
    roleRepository,
    accountDao,
    permissionDao,
    personalPathDao,
    repoAuthConfigDao,
    repoClient,
    projectClient,
) {

    override fun listPermissionRepo(projectId: String, userId: String, appId: String?): List<String> {
        // 用户为系统管理员，或者当前项目管理员
        if (isUserSystemAdmin(userId) || isUserLocalProjectAdmin(userId, projectId)
            || isDevopsProjectMember(userId, projectId, READ.name)
        ) return getAllRepoByProjectId(projectId)

        return super.listPermissionRepo(projectId, userId, appId)
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {

        // 校验平台账号操作范围
        if (request.appId != null && !checkPlatformPermission(request)) return false

        // bkiamv3权限校验
        if (matchBkiamv3Cond(request)) {
            // 当有v3权限时，返回成功；如没有v3权限则按devops账号体系继续进行判断
            if (checkBkIamV3Permission(request)) return true
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
        if (isDevopsProjectAdmin(userId, projectId)) {
            return emptyList()
        }
        return super.listNoPermissionPath(userId, projectId, repoName)
    }

    override fun getPathCheckConfig(): Boolean {
        return devopsAuthConfig.enablePathCheck
    }

    override fun listExternalRoleByProject(projectId: String, source: RoleSource): List<ExternalRoleResult> {
        if (source == RoleSource.DEVOPS) {
            return devopsProjectService.listRoleAndUserByProject(projectId)
        }
        return emptyList()
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

            if (isUserSystemAdmin(uid)) return true

            // 用户不为系统管理员，必须为项目下权限
            if (projectId == null) return false

            if (isDevopsProjectAdmin(uid, projectId!!) || isUserLocalProjectAdmin(uid, projectId)) {
                logger.debug("user is devops/local project admin [$uid, $projectId]")
                return true
            }

            val pass = when (resourceType) {
                PROJECT.name -> checkProjectPermission(request)
                REPO.name, NODE.name -> checkRepoOrNodePermission(request)
                else -> throw RuntimeException("resource type not supported: $resourceType")
            }

            if (!pass && matchDevopsCond(appId)) {
                logger.warn("devops forbidden [$request]")
            } else {
                logger.debug("devops pass [$request]")
            }
            return pass
        }
    }

    private fun checkProjectPermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            // 只有用户为非项目管理员，代码才会走到这里, action为MANAGE需要项目管理员权限
            if (action == MANAGE.name) {
                logger.debug("project request need manage permission [$request]")
                return false
            }
            return isDevopsProjectMember(uid, projectId!!, action)
                    || checkBkIamV3ProjectPermission(projectId!!, uid, action)
        }
    }

    private fun checkRepoOrNodePermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            if (action == MANAGE.name) {
                logger.debug("project request need manage permission [$request]")
                return false
            }
            when (repoName) {
                CUSTOM, LOG -> {
                    return checkDevopsCustomPermission(request)
                }
                PIPELINE -> {
                    return checkDevopsPipelinePermission(request)
                }
                REPORT -> {
                    return checkDevopsReportPermission(request.action)
                }
                else -> {
                    return checkRepoNotInDevops(request)
                }
            }
        }
    }

    private fun checkDevopsReportPermission(action: String): Boolean {
        return action == READ.name || action == WRITE.name || action == VIEW.name
    }

    private fun checkDevopsCustomPermission(request: CheckPermissionRequest): Boolean {
        logger.debug("check devops custom permission request [$request]")
        with(request) {
            val isDevopsProjectMember = isDevopsProjectMember(uid, projectId!!, action)
            if (needCheckPathPermission(resourceType, projectId!!, repoName!!)) {
                return checkNodeAction(request, null, isDevopsProjectMember)
            }
            return isDevopsProjectMember
        }
    }

    private fun checkRepoNotInDevops(request: CheckPermissionRequest): Boolean {
        logger.debug("check repo not in devops request [$request]")
        with(request) {
            val isDevopsProjectMember = isDevopsProjectMember(uid, projectId!!, action)
            if (needCheckPathPermission(resourceType, projectId!!, repoName!!)) {
                return checkNodeAction(request, null, isDevopsProjectMember)
            }
            return super.checkPermission(request) || isDevopsProjectMember
        }
    }

    private fun needCheckPathPermission(resourceType: String, projectId: String, repoName: String): Boolean {
        return devopsAuthConfig.enablePathCheck && resourceType == NODE.name && needNodeCheck(projectId, repoName)
    }

    private fun checkDevopsPipelinePermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            return when (resourceType) {
                REPO.name -> isDevopsProjectMember(uid, projectId!!, action)
                NODE.name -> {
                    val pipelineId = parsePipelineId(path ?: return false) ?: return false
                    val pipelinePass = pipelinePermission(uid, projectId!!, pipelineId, action)
                    if (pipelinePass) return true
                    logger.warn("devops pipeline permission widen to project permission [$request]")
                    return isDevopsProjectMember(uid, projectId!!, action)
                }
                else -> throw RuntimeException("resource type not supported: $resourceType")
            }
        }

    }

    private fun isDevopsProjectMember(userId: String, projectId: String, action: String): Boolean {
        logger.debug("isDevopsProjectMember: [$userId,$projectId,$action]")
        return devopsProjectService.isProjectMember(userId, projectId, action)
    }

    private fun isDevopsProjectAdmin(userId: String, projectId: String): Boolean {
        logger.debug("isDevopsProjectAdmin: [$userId,$projectId]")
        return devopsProjectService.isProjectManager(userId, projectId)
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