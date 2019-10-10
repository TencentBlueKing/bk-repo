package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceProjectResource
import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TRolePermission
import com.tencent.bkrepo.auth.pojo.*
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.repository.ProjectRepository
import com.tencent.bkrepo.auth.repository.RolePermissionRepository
import com.tencent.bkrepo.auth.service.ProjectService
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceProjectResourceImpl @Autowired constructor(
    private val projectService: ProjectService
) : ServiceProjectResource{
    override fun createProject(request: CreateProjectRequest): Response<Boolean> {
        projectService.createProject(request)
        return Response(true)
    }

    override fun deleteByName(name: String): Response<Boolean> {
        projectService.deleteByName(name)
        return Response(true)
    }

    override fun listProject(): Response<List<Project>> {
        return Response(projectService.listProject())
    }
}
