package com.tencent.bkrepo.auth.service.bkiam

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.RegisterResourceRequest
import com.tencent.bkrepo.auth.pojo.ResourceBaseRequest
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.SystemCode
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.local.PermissionServiceImpl
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate

class BkiamPermissionServiceImpl constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val mongoTemplate: MongoTemplate,
    private val repositoryClient: RepositoryClient,
    private val bkiamService: BkiamService
) : PermissionServiceImpl(userRepository, roleRepository, permissionRepository, mongoTemplate, repositoryClient) {

    override fun checkPermission(request: CheckPermissionRequest): Boolean {
        logger.info("checkPermission, request: $request")
        if (request.resourceType != ResourceType.SYSTEM && checkBkiamPermission(request)) {
            logger.debug("checkBkiamPermission passed")
            return true
        }
        return super.checkPermission(request)
    }

    override fun registerResource(request: RegisterResourceRequest) {
        logger.info("registerResource, request: $request")
        val resourceId = getResourceId(request)
        bkiamService.createResource(
            userId = request.uid,
            systemCode = SystemCode.BKREPO,
            projectId = request.projectId!!,
            resourceType = request.resourceType,
            resourceId = resourceId,
            resourceName = resourceId
        )
    }

    private fun checkBkiamPermission(request: CheckPermissionRequest): Boolean {
        return bkiamService.validateResourcePermission(
            userId = request.uid,
            systemCode = SystemCode.BKREPO,
            projectId = request.projectId!!,
            resourceType = request.resourceType,
            action = request.action,
            resourceId = getResourceId(request)
        )
    }

    private fun getResourceId(request: ResourceBaseRequest): String {
        return when (request.resourceType) {
            ResourceType.SYSTEM -> StringPool.EMPTY
            ResourceType.PROJECT -> request.projectId!!
            ResourceType.REPO -> request.repoName!!
            ResourceType.NODE -> throw IllegalArgumentException("invalid resource type")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkiamPermissionServiceImpl::class.java)
    }
}
