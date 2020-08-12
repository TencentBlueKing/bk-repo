package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.dao.repository.ProjectRepository
import com.tencent.bkrepo.repository.listener.event.project.ProjectCreatedEvent
import com.tencent.bkrepo.repository.model.TProject
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class ProjectService(
    private val projectRepository: ProjectRepository
) : AbstractService() {
    fun query(name: String): ProjectInfo? {
        return convert(queryProject(name))
    }

    fun list(): List<ProjectInfo> {
        return projectRepository.findAll().map { convert(it)!! }
    }

    fun exist(name: String): Boolean {
        return queryProject(name) != null
    }

    fun create(request: ProjectCreateRequest): ProjectInfo {
        with(request) {
            validateParameter(this)
            val project = TProject(
                name = name,
                displayName = displayName,
                description = description.orEmpty(),
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )
            return projectRepository.insert(project)
                .also { createProjectManager(it.name, it.createdBy) }
                .also { publishEvent(ProjectCreatedEvent(request)) }
                .also { logger.info("Create project [$request] success.") }
                .let { convert(it)!! }
        }
    }

    fun checkProject(name: String) {
        if (!exist(name)) throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, name)
    }

    private fun queryProject(name: String): TProject? {
        if (name.isBlank()) return null

        val criteria = Criteria.where(TProject::name.name).`is`(name)
        return mongoTemplate.findOne(Query(criteria), TProject::class.java)
    }

    private fun validateParameter(request: ProjectCreateRequest) {
        with(request) {
            if (!Pattern.matches(PROJECT_NAME_PATTERN, name)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::name.name)
            }
            if (displayName.isBlank() || displayName.length < DISPLAY_NAME_LENGTH_MIN || displayName.length > DISPLAY_NAME_LENGTH_MAX) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::displayName.name)
            }
            if (exist(name)) {
                throw ErrorCodeException(ArtifactMessageCode.PROJECT_EXISTED, name)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectService::class.java)
        private const val PROJECT_NAME_PATTERN = "[a-z][a-zA-Z0-9\\-_]{1,31}"
        private const val DISPLAY_NAME_LENGTH_MIN = 1
        private const val DISPLAY_NAME_LENGTH_MAX = 32

        private fun convert(tProject: TProject?): ProjectInfo? {
            return tProject?.let {
                ProjectInfo(
                    name = it.name,
                    displayName = it.displayName,
                    description = it.description,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }
    }
}
