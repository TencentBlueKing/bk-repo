package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.service.ProjectService
import org.springframework.web.bind.annotation.RestController

/**
 * 项目服务接口实现类
 */
@RestController
class ProjectController(
    private val projectService: ProjectService
) : ProjectClient {

    override fun query(name: String): Response<ProjectInfo?> {
        return ResponseBuilder.success(projectService.query(name))
    }

    override fun list(): Response<List<ProjectInfo>> {
        return ResponseBuilder.success(projectService.list())
    }

    override fun create(request: ProjectCreateRequest): Response<ProjectInfo> {
        return ResponseBuilder.success(projectService.create(request))
    }
}
