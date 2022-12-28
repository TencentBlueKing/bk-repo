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
import com.tencent.bk.sdk.iam.dto.manager.dto.CreateSubsetManagerDTO
import com.tencent.bk.sdk.iam.dto.manager.dto.ManagerMemberGroupDTO
import com.tencent.bk.sdk.iam.dto.manager.dto.ManagerRoleGroupDTO
import com.tencent.bk.sdk.iam.helper.AuthHelper
import com.tencent.bk.sdk.iam.service.ManagerService
import com.tencent.bk.sdk.iam.service.PolicyService
import com.tencent.bk.sdk.iam.service.v2.V2ManagerService
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_NAME
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_BKIAMV3
import com.tencent.bkrepo.auth.constant.AUTH_CONFIG_TYPE_VALUE_DEVOPS
import com.tencent.bkrepo.auth.model.TBkIamAuthManager
import com.tencent.bkrepo.auth.pojo.enums.DefaultGroupType
import com.tencent.bkrepo.auth.pojo.enums.DefaultGroupTypeAndActions
import com.tencent.bkrepo.auth.pojo.enums.ResourceActionMapping
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.iam.ResourceInfo
import com.tencent.bkrepo.auth.repository.BkIamAuthManagerRepository
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
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
@ConditionalOnExpression(
    "'\${$AUTH_CONFIG_PREFIX.$AUTH_CONFIG_TYPE_NAME}'.equals('$AUTH_CONFIG_TYPE_VALUE_DEVOPS')" +
        " or '\${$AUTH_CONFIG_PREFIX.$AUTH_CONFIG_TYPE_NAME}'.equals('$AUTH_CONFIG_TYPE_VALUE_BKIAMV3')"
)
class BkIamV3ServiceImpl(
    private val iamConfiguration: IamConfiguration,
    private val authHelper: AuthHelper,
    private val projectClient: ProjectClient,
    private val managerService: V2ManagerService,
    private val managerServiceV1: ManagerService,
    private val policyService: PolicyService,
    private val repositoryClient: RepositoryClient,
    private val nodeClient: NodeClient,
    private val authManagerRepository: BkIamAuthManagerRepository,
    val mongoTemplate: MongoTemplate
    ) : BkIamV3Service, BkiamV3BaseService(mongoTemplate) {


    private val iamAuthCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build<String, Boolean>()

    override fun checkIamConfiguration(): Boolean {
        if (iamConfiguration.systemId.isNullOrEmpty()) {
            return false
        }
        if (iamConfiguration.apigwBaseUrl.isNullOrEmpty()) {
            return false
        }
        if (iamConfiguration.appCode.isNullOrEmpty()) {
            return false
        }
        if (iamConfiguration.appSecret.isNullOrEmpty()) {
            return false
        }
        return true
    }

    override fun getPermissionUrl(
        userId: String,
        projectId: String,
        repoName: String?,
        resourceType: String,
        action: String,
        resourceId: String,
    ): String? {
        logger.debug(
            "v3 getPermissionUrl, userId: $userId, projectId: $projectId, repoName: $repoName" +
                " resourceType: $resourceType, action: $action, resourceId: $resourceId"
        )
        if (!checkIamConfiguration()) return null
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
        logger.info("v3 get permissionUrl pUrlRequest: $pUrlRequest")
        val pUrl = try {
            managerServiceV1.getPermissionUrl(pUrlRequest)
        } catch (e: Exception) {
            logger.error( "v3 getPermissionUrl with userId: $userId, action: $action," +
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
            "v3 validateResourcePermission, userId: $userId, projectId: $projectId, repoName: $repoName" +
                " resourceType: $resourceType, action: $action, resourceId: $resourceId, appId: $appId"
        )
        if (!checkIamConfiguration()) return false
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
        cachedResult?.let {
            logger.debug("v3 validateResourcePermission match in cache: $cacheKey|$cachedResult")
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
            "v3 isAllowed $allowed for userId: $userId, action: $action, instanceDTO: $instanceDTO"
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
            "v3 listPermissionResources, userId: $userId, projectId: $projectId" +
                " resourceType: $resourceType, action: $action"
        )
        if (!checkIamConfiguration()) return emptyList()
        val actionDto = ActionDTO()
        actionDto.id = action
        val expression = policyService.getPolicyByAction(userId, actionDto, null)
        if (expression == null || expression.isEmpty) return emptyList()
        logger.debug("v3 expression is $expression, and resourceType is $resourceType")
        return when(resourceType) {
            ResourceType.PROJECT.id() -> {
                getProjects(expression)
            }
            ResourceType.REPO.id() -> {
                val idList = getResourceInstance(expression, projectId!!, resourceType).map {
                    it.removePrefix("$projectId${StringPool.COLON}")
                }
                convertRepoResourceIdToRepoName(idList).map {
                    it[RepositoryInfo::name.name].toString()
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
        projectId: String,
        repoName: String?
    ): String? {
        if (!checkIamConfiguration()) return null
        return if (repoName == null) {
            createProjectGradeManager(userId, projectId)
        } else {
            createRepoGradeManager(userId, projectId, repoName)
        }
    }

    override fun deleteRepoGradeManager(userId: String, projectId: String, repoName: String): Boolean {
        if (!checkIamConfiguration()) return false
        val managerId = authManagerRepository.findByTypeAndResourceIdAndParentResId(
            ResourceType.REPO, repoName, projectId
        )?.managerId ?: return true
        // TODO 等待提供接口
        return true
    }

    fun createProjectGradeManager(
        userId: String,
        projectId: String
    ): String? {
        val projectInfo = projectClient.getProjectInfo(projectId).data!!
        logger.debug("v3 start to create grade manager for project $projectId with user $userId")
        // 授权人员范围默认设置为全部人员
        val iamSubjectScopes = listOf(ManagerScopes(ManagerScopesEnum.getType(ManagerScopesEnum.ALL), "*"))
        val projectResInfo = ResourceInfo(projectInfo.name, projectInfo.displayName, ResourceType.PROJECT)
        val authorizationScopes = BkIamV3Utils.buildManagerResources(
            projectResInfo = projectResInfo,
            resActionList = ResourceActionMapping.values().toList(),
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
            logger.error("v3 create grade manager for project ${projectInfo.name} error: ${e.message}")
            return null
        }
        logger.debug("v3 The id of project [${projectInfo.name}]'s grade manager is $managerId")
        saveTBkIamAuthManager(projectId, null, managerId, userId)
        batchCreateDefaultGroups(
            userId = userId,
            gradeManagerId = managerId,
            projectResInfo = projectResInfo,
            members = setOf(userId),
            groupList = listOf(
                DefaultGroupType.PROJECT_MANAGER,
                DefaultGroupType.PROJECT_EDIT,
                DefaultGroupType.PROJECT_DOWNLOAD,
                DefaultGroupType.PROJECT_UPLOAD_DELETE
            )
        )
        return managerId.toString()
    }


    /**
     * 创建项目分级管理员
     */
    fun createRepoGradeManager(
        userId: String,
        projectId: String,
        repoName: String
    ): String? {
        val projectInfo = projectClient.getProjectInfo(projectId).data!!
        val repoDetail = repositoryClient.getRepoInfo(projectId, repoName).data!!
        logger.debug("v3 start to create grade manager for repo $projectId|$repoName")
        val projectResInfo = ResourceInfo(projectInfo.name, projectInfo.displayName, ResourceType.PROJECT)
        val repoResInfo = ResourceInfo(repoDetail.id!!, repoDetail.name, ResourceType.REPO)
        // 授权人员范围默认设置为全部人员
        val iamSubjectScopes = listOf(ManagerScopes(ManagerScopesEnum.getType(ManagerScopesEnum.ALL), "*"))
        val authorizationScopes = BkIamV3Utils.buildManagerResources(
            projectResInfo = projectResInfo,
            repoResInfo = repoResInfo,
            resActionList = listOf(ResourceActionMapping.REPO_ACTIONS, ResourceActionMapping.NODE_ACTIONS),
            iamConfiguration = iamConfiguration
        )
        try {
        // 如果项目没有创建managerId,则补充创建
        var projectManagerId = authManagerRepository.findByTypeAndResourceIdAndParentResId(
            ResourceType.PROJECT, projectId, null
        )?.managerId ?: createProjectGradeManager(projectInfo.createdBy, projectId)
        if (projectManagerId == null) {
            projectManagerId = createProjectGradeManager(userId, projectId)
        }
        logger.debug("v3 create grade manager for repo [${projectInfo.name}|$repoName]," +
                         " projectManagerId: $projectManagerId")
        val secondManagerMembers = mutableSetOf<String>()
        secondManagerMembers.add(userId)
        val createRepoManagerDTO = CreateSubsetManagerDTO.builder()
            .name("$SYSTEM_DEFAULT_NAME-$PROJECT_DEFAULT_NAME-${projectInfo.displayName}" +
                      "-$REPO_DEFAULT_NAME-${repoDetail.name}")
            .description(IamGroupUtils.buildManagerDescription("${projectInfo.displayName}-${repoDetail.name}", userId))
            .members(secondManagerMembers.toList())
            .authorizationScopes(authorizationScopes)
            .subjectScopes(iamSubjectScopes).build()

        val repoManagerId=  managerService.createSubsetManager(projectManagerId.toString(), createRepoManagerDTO)
        logger.debug("v3 The id of repo [${projectInfo.name}|$repoName]'s grade manager is $repoManagerId")
        saveTBkIamAuthManager(projectId, repoName, repoManagerId, userId)
        batchCreateDefaultGroups(
            userId = userId,
            gradeManagerId = repoManagerId,
            projectResInfo = projectResInfo,
            repoResInfo = repoResInfo,
            members = secondManagerMembers,
            groupList = listOf(
                DefaultGroupType.REPO_MANAGER,
                DefaultGroupType.REPO_DOWNLOAD,
                DefaultGroupType.REPO_UPLOAD_DELETE
            )
        )
        return repoManagerId.toString()
        } catch (e: Exception) {
            logger.error("v3 create grade manager for repo [${projectInfo.name}|$repoName] error: ${e.message}")
            return null
        }
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

    private fun saveTBkIamAuthManager(
        projectId: String,
        repoName: String?,
        managerId: Int,
        userId: String
    ) {
        val tBkIamAuthManager = if (repoName == null) {
            TBkIamAuthManager(
                resourceId = projectId,
                type = ResourceType.PROJECT,
                managerId = managerId,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now())
        } else {
            TBkIamAuthManager(
                resourceId = repoName,
                type = ResourceType.REPO,
                managerId = managerId,
                parentResId = projectId,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
        }
        authManagerRepository.save(
            tBkIamAuthManager
        )
    }

    /**
     * 批量创建默认group
     */
    private fun batchCreateDefaultGroups(
        userId: String,
        gradeManagerId: Int,
        projectResInfo: ResourceInfo,
        repoResInfo: ResourceInfo? = null,
        members: Set<String>,
        groupList: List<DefaultGroupType>
    ) {
        groupList.forEach {
            createDefaultGroup(
                userId = userId,
                gradeManagerId = gradeManagerId,
                projectResInfo = projectResInfo,
                repoResInfo = repoResInfo,
                defaultGroupType = it,
                members = members
            )
        }
    }

    /**
     * 创建默认用户组
     */
    private fun createDefaultGroup(
        userId: String,
        gradeManagerId: Int,
        projectResInfo: ResourceInfo,
        repoResInfo: ResourceInfo? = null,
        defaultGroupType: DefaultGroupType,
        members: Set<String>
    ) {
        logger.debug("v3 start to create default group $defaultGroupType for $projectResInfo|$repoResInfo")
        val (resName, resType) = if (repoResInfo == null) {
            Pair(projectResInfo.resName, projectResInfo.resType)
        } else {
            Pair(repoResInfo.resName, repoResInfo.resType)
        }
        val defaultGroup = ManagerRoleGroup(
            IamGroupUtils.buildIamGroup(resName, defaultGroupType.displayName),
            IamGroupUtils.buildDefaultDescription(resName, defaultGroupType.displayName, userId),
            // 管理员组只允许读，不可编辑
            false
        )
        val managerRoleGroup = ManagerRoleGroupDTO.builder().groups(listOf(defaultGroup)).build()
        val roleId = try {
            when(resType) {
                ResourceType.PROJECT -> managerService.batchCreateRoleGroupV2(gradeManagerId, managerRoleGroup)
                ResourceType.REPO -> managerService.batchCreateSubsetRoleGroup(gradeManagerId, managerRoleGroup)
                else -> return
            }
        } catch (e: Exception) {
            logger.error("v3 batch create role for $projectResInfo|$repoResInfo error: ${e.message}")
            return
        }
        logger.debug("v3 The id of default group $defaultGroupType for $projectResInfo|$repoResInfo is $roleId")
        // 赋予权限
        try {
            createRoleGroupMember(defaultGroupType, roleId, members)
            val actions = DefaultGroupTypeAndActions.get(defaultGroupType.name.toLowerCase()).actions
            grantGroupPermission(projectResInfo, repoResInfo, roleId, actions)
        } catch (e: Exception) {
            managerService.deleteRoleGroupV2(roleId)
            logger.error(
                "v3 create iam group permission fail : $projectResInfo|$repoResInfo" +
                    " iamRoleId = $roleId | groupInfo = ${defaultGroupType.value}",
                e
            )
        }
    }

    private fun createRoleGroupMember(defaultGroupType: DefaultGroupType, roleId: Int, userIds: Set<String>) {
        if (defaultGroupType != DefaultGroupType.PROJECT_MANAGER && defaultGroupType != DefaultGroupType.REPO_MANAGER) {
            return
        }
        val groupMembers =  userIds.map { ManagerMember(ManagerScopesEnum.getType(ManagerScopesEnum.USER), it) }
        val expired = System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(DEFAULT_EXPIRED_AT)
        val managerMemberGroup = ManagerMemberGroupDTO.builder().members(groupMembers)
            .expiredAt(expired).build()
        // 项目创建人添加至管理员分组
        managerService.createRoleGroupMemberV2(roleId, managerMemberGroup)
    }

    /**
     * 用户组授权
     */
    private fun grantGroupPermission(
        projectResInfo: ResourceInfo,
        repoResInfo: ResourceInfo? = null,
        roleId: Int,
        actions: Map<String, List<String>>
    ) {
        logger.debug("v3 grant role permission for group $roleId in $projectResInfo|$repoResInfo with actions $actions")
        try {
            actions.forEach{
                val permission = buildResource(
                    projectResInfo = projectResInfo,
                    repoResInfo = repoResInfo,
                    iamConfiguration = iamConfiguration,
                    actions = it.value,
                    resourceType = it.key
                )
                managerService.grantRoleGroupV2(roleId, permission)
            }
        } catch (e: Exception) {
            logger.error(
                "v3 create role permission for $projectResInfo|$repoResInfo with actions $actions error: ${e.message}"
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkIamV3ServiceImpl::class.java)
        private const val DEFAULT_EXPIRED_AT = 365L // 用户组默认一年有效期
        private const val SYSTEM_DEFAULT_NAME = "制品库"
        private const val PROJECT_DEFAULT_NAME = "项目"
        private const val REPO_DEFAULT_NAME = "仓库"
    }
}
