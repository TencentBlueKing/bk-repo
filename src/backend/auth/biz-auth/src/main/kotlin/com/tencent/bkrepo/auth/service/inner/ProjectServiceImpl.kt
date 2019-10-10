package com.tencent.bkrepo.auth.service.inner

import com.tencent.bkrepo.auth.model.TProject
import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.auth.pojo.Project
import com.tencent.bkrepo.auth.repository.ProjectRepository
import com.tencent.bkrepo.auth.service.ProjectService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "inner")
class ProjectServiceImpl @Autowired constructor(
    private val projectRepository: ProjectRepository
) : ProjectService {
    override fun listProject(): List<Project> {
        return projectRepository.findAll().map { transfer(it) }
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

    private fun transfer(tProject: TProject): Project {
        return Project(
            id = tProject.id!!,
            name = tProject.name,
            displayName = tProject.displayName,
            description = tProject.description
        )
    }

    override fun deleteByName(name: String) {
        projectRepository.deleteByName(name)

    }
}