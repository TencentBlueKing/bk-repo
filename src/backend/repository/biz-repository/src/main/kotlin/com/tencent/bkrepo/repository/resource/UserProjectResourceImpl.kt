package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.UserProjectResource
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.UserProjectCreateRequest
import com.tencent.bkrepo.repository.service.ProjectService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class UserProjectResourceImpl @Autowired constructor(
    private val projectService: ProjectService
) : UserProjectResource {

    override fun create(userId: String, userProjectRequest: UserProjectCreateRequest): Response<Void> {
        val createRequest = with(userProjectRequest) {
            ProjectCreateRequest(
                name = name,
                displayName = displayName,
                description = description,
                operator = userId
            )
        }
        projectService.create(createRequest)
        return ResponseBuilder.success()
    }
}
