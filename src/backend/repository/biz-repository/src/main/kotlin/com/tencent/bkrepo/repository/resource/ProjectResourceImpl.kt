package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.ProjectResource
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.service.ProjectService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ProjectResourceImpl @Autowired constructor(
    private val projectService: ProjectService
) : ProjectResource {

    override fun query(name: String): Response<ProjectInfo?> {
        return ResponseBuilder.success(projectService.query(name))
    }

    override fun list(): Response<List<ProjectInfo>> {
        return ResponseBuilder.success(projectService.list())
    }

    override fun create(request: ProjectCreateRequest): Response<Void> {
        projectService.create(request)
        return ResponseBuilder.success()
    }
}
