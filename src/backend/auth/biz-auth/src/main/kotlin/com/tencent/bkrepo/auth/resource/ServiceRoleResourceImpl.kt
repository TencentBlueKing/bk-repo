package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_NAME
import com.tencent.bkrepo.auth.constant.REPO_MANAGE_ID
import com.tencent.bkrepo.auth.constant.REPO_MANAGE_NAME
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceRoleResourceImpl @Autowired constructor(
    private val roleService: RoleService
) : ServiceRoleResource {

    override fun createRole(request: CreateRoleRequest): Response<String?> {
        // todo check request
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    override fun createProjectManage(projectId: String): Response<String?> {
        val request = CreateRoleRequest(PROJECT_MANAGE_ID, PROJECT_MANAGE_NAME, RoleType.PROJECT, projectId, null, true)
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    override fun createRepoManage(projectId: String, repoName: String): Response<String?> {
        val request = CreateRoleRequest(REPO_MANAGE_ID, REPO_MANAGE_NAME, RoleType.REPO, projectId, repoName, true)
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    override fun deleteRole(id: String): Response<Boolean> {
        // todo check request
        roleService.deleteRoleByid(id)
        return ResponseBuilder.success(true)
    }

    override fun detail(id: String): Response<Role?> {
        return ResponseBuilder.success(roleService.detail(id))
    }

    override fun listRole(
        type: RoleType?,
        projectId: String?,
        repoName: String?
    ): Response<List<Role>> {
        return ResponseBuilder.success(roleService.listRoleByProject(type, projectId, repoName))
    }
}
