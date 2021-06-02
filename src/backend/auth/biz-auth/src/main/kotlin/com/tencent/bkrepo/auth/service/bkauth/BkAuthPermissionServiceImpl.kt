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
import com.tencent.bkrepo.auth.pojo.enums.BkAuthPermission
import com.tencent.bkrepo.auth.pojo.enums.BkAuthResourceType
import com.tencent.bkrepo.auth.pojo.enums.BkAuthServiceCode
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
    private val bkAuthService: BkAuthService,
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
            // // 网关请求不允许匿名访问
            // if (appId == bkAuthConfig.bkrepoAppId && request.uid == ANONYMOUS_USER) {
            //     if (request.uid == ANONYMOUS_USER) {
            //         logger.warn("no anonymous access")
            //         return false
            //     }
            // }
            logger.info("check devops permission request [$request]")
            // devops请求，根据配置允许匿名访问
            if (appId == bkAuthConfig.devopsAppId &&
                request.uid == ANONYMOUS_USER &&
                bkAuthConfig.devopsAllowAnonymous
            ) {
                logger.warn("devops anonymous pass[$appId|$uid|$resourceType|$projectId|$repoName|$path|$action]")
                return true
            }

            // 校验蓝盾平台账号项目权限
            if (request.resourceType == ResourceType.PROJECT) {
                // devops直接放过
                if (request.appId == bkAuthConfig.devopsAppId) return true
                // 其它请求校验项目权限
                return checkProjectPermission(uid, projectId!!)
            }

            // 其它请求根据仓库类型判断
            val pass = when (repoName) {
                CUSTOM, LOG -> {
                    checkProjectPermission(uid, projectId!!)
                }
                PIPELINE -> {
                    checkPipelinePermission(uid, projectId!!, path, resourceType)
                }
                REPORT -> {
                    action == PermissionAction.READ || action == PermissionAction.WRITE
                }
                else -> {
                    checkProjectPermission(uid, projectId!!)
                }
            }

            // 校验不通过的权限只输出日志，暂时不拦截
            if (bkAuthConfig.devopsAuthEnabled) {
                return if (!bkAuthConfig.devopsAuthEnabled) {
                    logger.warn("devops forbidden[$appId|$uid|$resourceType|$projectId|$repoName|$path|$action]")
                    true
                } else {
                    logger.info("devops forbidden[$appId|$uid|$resourceType|$projectId|$repoName|$path|$action]")
                    false
                }
            }

            logger.info("devops pass[$appId|$uid|$resourceType|$projectId|$repoName|$path|$action]")
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
        logger.info("checkPipelinePermission, uid: $uid, projectId: $projectId, pipelineId: $pipelineId")
        return try {
            return bkAuthService.validateUserResourcePermission(
                user = uid,
                serviceCode = BkAuthServiceCode.PIPELINE,
                resourceType = BkAuthResourceType.PIPELINE_DEFAULT,
                projectCode = projectId,
                resourceCode = pipelineId,
                permission = BkAuthPermission.DOWNLOAD,
                retryIfTokenInvalid = true
            )
        } catch (e: Exception) {
            // TODO 调用auth稳定后改为抛异常
            logger.warn("checkPipelinePermission error:  ${e.message}")
            true
        }
    }

    private fun checkProjectPermission(uid: String, projectId: String): Boolean {
        logger.info("checkProjectPermission: uid: $uid, projectId: $projectId")
        return try {
            bkAuthProjectService.isProjectMember(uid, projectId, retryIfTokenInvalid = true)
        } catch (e: Exception) {
            // TODO 调用auth稳定后改为抛异常
            logger.warn("checkPipelinePermission error:  ${e.message}")
            true
        }
    }

    private fun isDevopsRepo(repoName: String): Boolean {
        return repoName == CUSTOM || repoName == PIPELINE || repoName == REPORT || repoName == LOG
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {

        // git ci项目校验单独权限
        if (request.projectId != null && request.projectId!!.startsWith(GIT_PROJECT_PREFIX, true)) {
            val context = PermissionRequestContext(
                userId = request.uid,
                projectId = request.projectId!!
            )
            logger.debug("check git project permission [$context]")
            pluginManager.findExtensionPoints(PermissionRequestExtension::class.java).forEach {
                return it.check(context)
            }
        }

        // 校验蓝盾平台账号项目权限
        // if (request.resourceType == ResourceType.PROJECT && request.appId == bkAuthConfig.devopsAppId) {
        //     return true
        // }

        // 校验蓝盾/网关平台账号指定仓库(pipeline/custom/report/log)的仓库和节点权限
        // val resourceCond = request.resourceType == ResourceType.REPO || request.resourceType == ResourceType.NODE
        // devops体系账号校验
        val appIdCond = request.appId == bkAuthConfig.devopsAppId ||
            request.appId == bkAuthConfig.bkrepoAppId ||
            request.appId == bkAuthConfig.bkcodeAppId
        if (appIdCond) {
            return checkDevopsPermission(request)
        }

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
