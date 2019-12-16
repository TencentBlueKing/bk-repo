package com.tencent.bkrepo.auth.service.inner

import com.tencent.bkrepo.auth.model.TProject
import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.auth.pojo.Project
import com.tencent.bkrepo.auth.repository.ProjectRepository
import com.tencent.bkrepo.auth.service.ProjectService
import com.tencent.bkrepo.auth.util.TransferUtils
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "inner")
class ProjectServiceImpl @Autowired constructor(
    private val projectRepository: ProjectRepository
) : ProjectService {
    override fun getByName(name: String): Project {
        val project = projectRepository.findOneByName(name) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)
        return TransferUtils.transferProject(project)
    }

    override fun listProject(): List<Project> {
        return projectRepository.findAll().map { TransferUtils.transferProject(it) }
    }

    override fun createProject(request: CreateProjectRequest) {
        projectRepository.insert(
            TProject(
                id = null,
                name = request.name,
                displayName = request.displayName,
                description = request.description
            )
        )
    }

    override fun deleteByName(name: String) {
        projectRepository.deleteByName(name)
    }
}