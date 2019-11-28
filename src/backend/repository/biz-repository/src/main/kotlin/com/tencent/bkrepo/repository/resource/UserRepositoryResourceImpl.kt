package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.repository.api.UserRepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.service.RepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 仓库服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class UserRepositoryResourceImpl @Autowired constructor(
    private val permissionService: PermissionService,
    private val repositoryService: RepositoryService
) : UserRepositoryResource {
    override fun create(userId: String, userRepoCreateRequest: UserRepoCreateRequest): Response<Void> {
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.PROJECT, PermissionAction.MANAGE, userRepoCreateRequest.projectId))

        val createRequest = with(userRepoCreateRequest) {
            RepoCreateRequest(
                projectId = projectId,
                name = name,
                type = type,
                category = category,
                public = public,
                description = description,
                configuration = configuration,
                storageCredentials = storageCredentials,
                operator = userId
            )
        }
        repositoryService.create(createRequest)
        return Response.success()
    }
}
