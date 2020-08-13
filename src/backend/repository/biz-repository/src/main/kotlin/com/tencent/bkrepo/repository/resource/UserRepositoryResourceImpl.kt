package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.UserRepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.service.RepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 仓库服务接口实现类
 */
@RestController
class UserRepositoryResourceImpl @Autowired constructor(
    private val permissionManager: PermissionManager,
    private val repositoryService: RepositoryService
) : UserRepositoryResource {

    @Principal(PrincipalType.PLATFORM)
    override fun create(userId: String, userRepoCreateRequest: UserRepoCreateRequest): Response<Void> {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.MANAGE, userRepoCreateRequest.projectId)

        val createRequest = with(userRepoCreateRequest) {
            RepoCreateRequest(
                projectId = projectId,
                name = name,
                type = type,
                category = category,
                public = public,
                description = description,
                configuration = configuration,
                storageCredentialsKey = storageCredentialsKey,
                operator = userId
            )
        }
        repositoryService.create(createRequest)
        return ResponseBuilder.success()
    }

    @Principal(PrincipalType.PLATFORM)
    override fun list(projectId: String): Response<List<RepositoryInfo>> {
        return ResponseBuilder.success(repositoryService.list(projectId))
    }
}
