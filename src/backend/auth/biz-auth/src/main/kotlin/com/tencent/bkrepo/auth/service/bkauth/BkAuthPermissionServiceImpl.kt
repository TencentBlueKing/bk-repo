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

import com.tencent.bkrepo.auth.config.BkAuthConfig
import com.tencent.bkrepo.auth.extension.PermissionRequestContext
import com.tencent.bkrepo.auth.extension.PermissionRequestExtension
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.local.PermissionServiceImpl
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.plugin.api.PluginManager
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * 对接蓝鲸权限中心v0
 */
class BkAuthPermissionServiceImpl constructor(
    userRepository: UserRepository,
    roleRepository: RoleRepository,
    permissionRepository: PermissionRepository,
    mongoTemplate: MongoTemplate,
    repositoryClient: RepositoryClient,
    private val bkAuthConfig: BkAuthConfig,
    private val bkAuthPipelineService: BkAuthPipelineService,
    private val bkAuthProjectService: BkAuthProjectService,
    private val pluginManager: PluginManager
) : PermissionServiceImpl(userRepository, roleRepository, permissionRepository, mongoTemplate, repositoryClient) {
    private fun parsePipelineId(path: String): String? {
        val roads = path.split("/")
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
            // project权限
            if (request.resourceType == ResourceType.PROJECT) {
                // devops直接放过
                if (request.appId == bkAuthConfig.devopsAppId) return true
                // 其它请求校验项目权限
                return checkProjectPermission(uid, projectId!!)
            }

            // repo或者node权限
            val pass = when (repoName) {
                CUSTOM, LOG -> {
                    checkProjectPermission(uid, projectId!!)
                }
                PIPELINE -> {
                    checkPipelinePermission(uid, projectId!!, path, resourceType) ||
                        checkProjectPermission(uid, projectId!!)
                }
                REPORT -> {
                    action == PermissionAction.READ || action == PermissionAction.WRITE
                }
                else -> {
                    // 有本地权限，或者蓝盾项目权限，放过
                    super.checkPermission(request) || checkProjectPermission(uid, projectId!!)
                }
            }

            // devops来源的账号，不做拦截
            if (!pass && appId == bkAuthConfig.devopsAppId) {
                logger.warn("devops forbidden[$appId|$uid|$resourceType|$projectId|$repoName|$path|$action]")
                return !bkAuthConfig.devopsAuthEnabled
            }

            logger.debug("devops pass[$appId|$uid|$resourceType|$projectId|$repoName|$path|$action]")
            return pass
        }
    }


    private fun checkPipelinePermission(
        uid: String,
        projectId: String,
        path: String?,
        resourceType: ResourceType
    ): Boolean {
        return when (resourceType) {
            ResourceType.REPO -> checkProjectPermission(uid, projectId)
            ResourceType.NODE -> {
                val pipelineId = parsePipelineId(path ?: return false) ?: return false
                checkPipelinePermission(uid, projectId, pipelineId)
            }
            else -> throw RuntimeException("resource type not supported: $resourceType")
        }
    }

    private fun checkPipelinePermission(uid: String, projectId: String, pipelineId: String): Boolean {
        logger.debug("checkPipelinePermission, uid: $uid, projectId: $projectId, pipelineId: $pipelineId")
        return try {
            return bkAuthPipelineService.hasPermission(uid, projectId, pipelineId)
        } catch (e: Exception) {
            // TODO 调用auth稳定后改为抛异常
            logger.warn("checkPipelinePermission error:  ${e.message}")
            true
        }
    }

    private fun checkProjectPermission(uid: String, projectId: String): Boolean {
        logger.debug("checkProjectPermission: uid: $uid, projectId: $projectId")
        return try {
            bkAuthProjectService.isProjectMember(uid, projectId, retryIfTokenInvalid = true)
        } catch (e: Exception) {
            // TODO 调用auth稳定后改为抛异常
            logger.warn("checkPipelinePermission error:  ${e.message}")
            true
        }
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {

        // git ci项目校验单独权限
        if (request.projectId!!.startsWith(GIT_PROJECT_PREFIX, true) &&
            bkAuthConfig.choseBkAuth() && request.projectId != null
        ) {
            val context = PermissionRequestContext(
                userId = request.uid,
                projectId = request.projectId!!
            )
            logger.debug("check git project permission [$context]")
            pluginManager.findExtensionPoints(PermissionRequestExtension::class.java).forEach {
                return it.check(context)
            }
        }

        // devops匿名访问请求处理
        with(request) {
            if (appId == bkAuthConfig.devopsAppId &&
                request.uid == ANONYMOUS_USER &&
                bkAuthConfig.devopsAllowAnonymous
            ) {
                logger.warn("devops anonymous pass[$appId|$uid|$resourceType|$projectId|$repoName|$path|$action]")
                return true
            }
        }

        // devops实名访问请求处理
        val appIdCond = request.appId == bkAuthConfig.devopsAppId ||
            request.appId == bkAuthConfig.bkrepoAppId ||
            request.appId == bkAuthConfig.bkcodeAppId
        if (appIdCond) {
            return checkDevopsPermission(request)
        }

        // 非devops体系
        return super.checkPermission(request)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkAuthPermissionServiceImpl::class.java)
        private const val CUSTOM = "custom"
        private const val PIPELINE = "pipeline"
        private const val REPORT = "report"
        private const val LOG = "log"
        private const val GIT_PROJECT_PREFIX = "git_"
    }
}
