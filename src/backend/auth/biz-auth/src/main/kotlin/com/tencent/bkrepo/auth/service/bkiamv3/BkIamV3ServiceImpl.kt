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

import com.google.common.cache.CacheBuilder
import com.tencent.bk.sdk.iam.config.IamConfiguration
import com.tencent.bk.sdk.iam.constants.ManagerScopesEnum
import com.tencent.bk.sdk.iam.dto.InstanceDTO
import com.tencent.bk.sdk.iam.dto.PathInfoDTO
import com.tencent.bk.sdk.iam.dto.PermissionUrlDTO
import com.tencent.bk.sdk.iam.dto.RelatedResourceTypes
import com.tencent.bk.sdk.iam.dto.RelationResourceInstance
import com.tencent.bk.sdk.iam.dto.action.ActionDTO
import com.tencent.bk.sdk.iam.dto.action.UrlAction
import com.tencent.bk.sdk.iam.dto.manager.ManagerMember
import com.tencent.bk.sdk.iam.dto.manager.ManagerRoleGroup
import com.tencent.bk.sdk.iam.dto.manager.ManagerScopes
import com.tencent.bk.sdk.iam.dto.manager.dto.CreateManagerDTO
import com.tencent.bk.sdk.iam.dto.manager.dto.ManagerMemberGroupDTO
import com.tencent.bk.sdk.iam.dto.manager.dto.ManagerRoleGroupDTO
import com.tencent.bk.sdk.iam.helper.AuthHelper
import com.tencent.bk.sdk.iam.service.ManagerService
import com.tencent.bk.sdk.iam.service.PolicyService
import com.tencent.bk.sdk.iam.service.v2.V2ManagerService
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_NAME
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_BKIAMV3
import com.tencent.bkrepo.auth.pojo.enums.DefaultGroupType
import com.tencent.bkrepo.auth.pojo.enums.DefaultGroupTypeAndActions
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.util.BkIamV3Utils
import com.tencent.bkrepo.auth.util.BkIamV3Utils.buildId
import com.tencent.bkrepo.auth.util.BkIamV3Utils.buildResource
import com.tencent.bkrepo.auth.util.BkIamV3Utils.getProjects
import com.tencent.bkrepo.auth.util.BkIamV3Utils.getResourceInstance
import com.tencent.bkrepo.auth.util.IamGroupUtils
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
@ConditionalOnProperty(
    prefix = AUTH_CONFIG_PREFIX, name = [AUTH_CONFIG_TYPE_NAME], havingValue = AUTH_CONFIG_TYPE_VALUE_BKIAMV3
)
class BkIamV3ServiceImpl(
    private val iamConfiguration: IamConfiguration,
    private val authHelper: AuthHelper,
    private val projectClient: ProjectClient,
    private val managerService: V2ManagerService,
    private val managerServiceV1: ManagerService,
    private val policyService: PolicyService,
    private val repositoryClient: RepositoryClient,
    private val nodeClient: NodeClient
    ) : BkIamV3Service {


    private val iamAuthCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build<String, Boolean>()

    override fun getPermissionUrl(
        userId: String,
        projectId: String,
        repoName: String?,
        resourceType: String,
        action: String,
        resourceId: String,
    ): String? {
        logger.debug(
            "getPermissionUrl, userId: $userId, projectId: $projectId, repoName: $repoName" +
                " resourceType: $resourceType, action: $action, resourceId: $resourceId"
        )
        val instanceList = mutableListOf<RelationResourceInstance>()

        val projectInstance = RelationResourceInstance(
            iamConfiguration.systemId,
            ResourceType.PROJECT.id(),
            projectId,
            null
        )
        instanceList.add(projectInstance)
        if (repoName != null) {
            val repoInstance = RelationResourceInstance(
                iamConfiguration.systemId,
                ResourceType.REPO.id(),
                convertRepoResourceId(projectId, repoName),
                null
            )
            instanceList.add(repoInstance)
            if (resourceType == ResourceType.NODE.id()) {
                val nodeInstance = RelationResourceInstance(
                    iamConfiguration.systemId,
                    ResourceType.NODE.id(),
                    resourceId,
                    null
                )
                instanceList.add(nodeInstance)
            }
        }
        val instances: List<List<RelationResourceInstance>> = listOf(instanceList)
        val relatedResourceTypes = RelatedResourceTypes(
            iamConfiguration.systemId,
            resourceType,
            instances,
            emptyList()
        )
        val actions = listOf(UrlAction(action, listOf(relatedResourceTypes)))

        val pUrlRequest = PermissionUrlDTO(
            iamConfiguration.systemId,
            actions
        )
        logger.info("get permissionUrl pUrlRequest: $pUrlRequest")
        val pUrl = try {
            managerServiceV1.getPermissionUrl(pUrlRequest)
        } catch (e: Exception) {
            logger.error( "getPermissionUrl with userId: $userId, action: $action," +
                              " pUrlRequest: $pUrlRequest\" error: ${e.message}")
            StringPool.EMPTY
        }
        return pUrl
    }

    override fun validateResourcePermission(
        userId: String,
        projectId: String,
        repoName: String?,
        resourceType: String,
        action: String,
        resourceId: String,
        appId: String?
    ): Boolean {
        logger.debug(
            "validateResourcePermission, userId: $userId, projectId: $projectId, repoName: $repoName" +
                " resourceType: $resourceType, action: $action, resourceId: $resourceId, appId: $appId"
        )
        val instanceDTO = InstanceDTO()
        instanceDTO.system = iamConfiguration.systemId
        instanceDTO.id = resourceId
        instanceDTO.type = resourceType

        val projectPath = PathInfoDTO()
        projectPath.type = ResourceType.PROJECT.id()
        projectPath.id = projectId
        if (repoName != null) {
            val repoPath = PathInfoDTO()
            repoPath.type = ResourceType.REPO.id()
            repoPath.id = convertRepoResourceId(projectId, repoName)
            if (resourceType == ResourceType.NODE.id()) {
                val nodePath = PathInfoDTO()
                nodePath.type = ResourceType.NODE.id()
                nodePath.id = resourceId
                repoPath.child = nodePath
            }
            projectPath.child = repoPath
        }
        instanceDTO.path = projectPath

        val cacheKey = userId + action + resourceId
        // 优先从缓存内获取
        val cachedResult = iamAuthCache.getIfPresent(cacheKey)
        if (cachedResult != null) {
            return cachedResult
        }
        var allowed: Boolean
        try {
            allowed = authHelper.isAllowed(userId, action, instanceDTO)
            iamAuthCache.put(cacheKey, allowed)
        } catch (e: Exception) {
            logger.error(
                "try bkiamv3 check with userId: $userId, action: $action," +
                    " instanceDTO: $instanceDTO\" error: ${e.message}"
            )
            allowed = false
        }
        logger.debug(
            "isAllowed $allowed for userId: $userId, action: $action, instanceDTO: $instanceDTO"
        )
        return allowed
    }

    override fun convertRepoResourceId(projectId: String, repoName: String): String? {
        return repositoryClient.getRepoInfo(projectId, repoName).data?.id
    }


    override fun convertNodeResourceId(projectId: String, repoName: String, fullPath: String): String? {
        val index = HashShardingUtils.shardingSequenceFor(projectId, 256).toString()
        val nodeId = nodeClient.getNodeDetail(projectId, repoName, fullPath).data?.nodeInfo?.id ?: return null
        return buildId(nodeId, index)
    }

    override fun listPermissionResources(
        userId: String,
        projectId: String?,
        resourceType: String,
        action: String,
    ): List<String> {
        logger.debug(
            "listPermissionResources, userId: $userId, projectId: $projectId" +
                " resourceType: $resourceType, action: $action"
        )
        val actionDto = ActionDTO()
        actionDto.id = action
        val expression = policyService.getPolicyByAction(userId, actionDto, null)
        if (expression == null || expression.isEmpty) return emptyList()
        logger.debug("expression is $expression, and resourceType is $resourceType")
        return when(resourceType) {
            ResourceType.PROJECT.id() -> {
                getProjects(expression)
            }
            ResourceType.REPO.id() -> {
                getResourceInstance(expression, projectId!!, resourceType).map {
                    it.removePrefix("$projectId${StringPool.COLON}")
                }
            }
            else -> emptyList()
         }
    }

    /**
     * 创建项目分级管理员
     */
    override fun createGradeManager(
        userId: String,
        projectId: String
    ): String? {
        val projectInfo = projectClient.getProjectInfo(projectId).data!!
        logger.debug("start to create grade manager for project ${projectInfo.name}")
        // 授权人员范围默认设置为全部人员
        val iamSubjectScopes = listOf(ManagerScopes(ManagerScopesEnum.getType(ManagerScopesEnum.ALL), "*"))

        val authorizationScopes = BkIamV3Utils.buildProjectManagerResources(
            projectId = projectInfo.name,
            projectName = projectInfo.displayName,
            iamConfiguration = iamConfiguration
        )
        val createManagerDTO = CreateManagerDTO.builder().system(iamConfiguration.systemId)
            .name("$SYSTEM_DEFAULT_NAME-$PROJECT_DEFAULT_NAME-${projectInfo.displayName}")
            .description(IamGroupUtils.buildManagerDescription(projectInfo.displayName, userId))
            .members(arrayListOf(userId))
            .authorization_scopes(authorizationScopes)
            .subject_scopes(iamSubjectScopes).build()
        val managerId = try {
            managerService.createManagerV2(createManagerDTO)
        } catch (e: Exception) {
            logger.error("create grade manager for project ${projectInfo.name} error: ${e.message}")
            return null
        }
        logger.debug("The id of project [${projectInfo.name}]'s grade manager is $managerId")
        batchCreateDefaultGroups(userId, managerId, projectId, projectInfo.name)
        return managerId.toString()
    }

    override fun getResourceId(resourceType: String, projectId: String?, repoName: String?, path: String?): String? {
        return when (resourceType) {
            ResourceType.SYSTEM.toString() -> StringPool.EMPTY
            ResourceType.PROJECT.toString() -> projectId!!
            ResourceType.REPO.toString() ->
                convertRepoResourceId(projectId!!, repoName!!)
            ResourceType.NODE.toString() ->
                convertNodeResourceId(projectId!!, repoName!!, path!!)
            else -> throw IllegalArgumentException("invalid resource type")
        }
    }

    /**
     * 批量创建默认group
     */
    private fun batchCreateDefaultGroups(
        userId: String,
        gradeManagerId: Int,
        projectId: String,
        projectName: String,
    ) {
        DefaultGroupType.values().forEach {
            createDefaultGroup(
                userId = userId,
                gradeManagerId = gradeManagerId,
                projectId = projectId,
                defaultGroupType = it,
                projectName = projectName
            )
        }
    }

    /**
     * 创建默认用户组
     */
    private fun createDefaultGroup(
        userId: String,
        gradeManagerId: Int,
        projectId: String,
        projectName: String,
        defaultGroupType: DefaultGroupType
    ) {
        logger.debug("start to create default group $defaultGroupType for project [$projectId|$projectName]")
        val defaultGroup = ManagerRoleGroup(
            IamGroupUtils.buildIamGroup(projectId, defaultGroupType.displayName),
            IamGroupUtils.buildDefaultDescription(projectId, defaultGroupType.displayName, userId),
            false
        )
        val managerRoleGroup = ManagerRoleGroupDTO.builder().groups(listOf(defaultGroup)).build()
        val roleId = try {
            managerService.batchCreateRoleGroupV2(gradeManagerId, managerRoleGroup)
        } catch (e: Exception) {
            logger.error("batch create role for project $projectId error: ${e.message}")
            return
        }
        logger.debug("The id of default group $defaultGroupType for project [$projectId|$projectName] is $roleId")
        // 赋予权限
        try {
            val actions = when (defaultGroupType) {
                DefaultGroupType.MANAGER -> {
                    val groupMember = ManagerMember(ManagerScopesEnum.getType(ManagerScopesEnum.USER), userId)
                    val groupMembers = mutableListOf<ManagerMember>()
                    groupMembers.add(groupMember)
                    val expired = System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(DEFAULT_EXPIRED_AT)
                    val managerMemberGroup = ManagerMemberGroupDTO.builder().members(groupMembers)
                        .expiredAt(expired).build()
                    // 项目创建人添加至管理员分组
                    managerService.createRoleGroupMemberV2(roleId, managerMemberGroup)
                    DefaultGroupTypeAndActions.PROJECT_MANAGER.actions
                }
                DefaultGroupType.DEVELOPER -> {
                    DefaultGroupTypeAndActions.PROJECT_DEVELOPER.actions
                }
                DefaultGroupType.TESTER -> {
                    DefaultGroupTypeAndActions.PROJECT_TESTER.actions
                }
                DefaultGroupType.MAINTAINER -> {
                    DefaultGroupTypeAndActions.PROJECT_MAINTAINER.actions
                }
            }
            grantGroupPermission(projectId, projectName, roleId, actions)
        } catch (e: Exception) {
            e.printStackTrace()
            managerService.deleteRoleGroupV2(roleId)
            logger.error(
                "create iam group permission fail : projectId = $projectId |" +
                    " iamRoleId = $roleId | groupInfo = ${defaultGroupType.value}",
                e
            )
        }
    }

    /**
     * 用户组授权
     */
    private fun grantGroupPermission(
        projectId: String,
        projectName: String,
        roleId: Int,
        actions: Map<String, String>
    ) {
        logger.debug("grant role permission for group $roleId in project $projectId with actions $actions")
        try {
            actions.forEach{
                val permission = buildResource(
                    projectId = projectId,
                    projectName = projectName,
                    iamConfiguration = iamConfiguration,
                    actions = it.value.split(","),
                    resourceType = it.key
                )
                managerService.grantRoleGroupV2(roleId, permission)
            }
        } catch (e: Exception) {
            logger.error("create role permission for project $projectId with actions $actions error: ${e.message}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkIamV3ServiceImpl::class.java)
        private const val DEFAULT_EXPIRED_AT = 365L // 用户组默认一年有效期
        private const val SYSTEM_DEFAULT_NAME = "制品库"
        private const val PROJECT_DEFAULT_NAME = "项目"
    }
}
