package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.model.TSeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.task.SeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.task.SeparationTaskRequest
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@TestConfiguration
class NodeArchiveSupportMongoTestConfig {

    @Bean
    @Primary
    fun separationTaskServiceForNodeArchiveMongoIt(mongoTemplate: MongoTemplate): SeparationTaskService {
        return object : SeparationTaskService {
            override fun findDistinctSeparationDate(
                projectId: String?,
                repoName: String?,
                taskType: String?,
            ): Set<LocalDateTime> {
                val result = mutableSetOf<LocalDateTime>()
                val criteria = if (taskType != null) {
                    Criteria.where(TSeparationTask::type.name).isEqualTo(taskType)
                } else {
                    Criteria.where(TSeparationTask::type.name).`in`(
                        SeparationTaskServiceImpl.SEPARATE,
                        SeparationTaskServiceImpl.SEPARATE_ARCHIVED,
                    )
                }
                projectId?.apply { criteria.and(TSeparationTask::projectId.name).isEqualTo(this) }
                repoName?.apply { criteria.and(TSeparationTask::repoName.name).isEqualTo(this) }
                mongoTemplate.find(Query(criteria), TSeparationTask::class.java).forEach {
                    result.add(it.separationDate)
                }
                return result
            }

            override fun createSeparationTask(request: SeparationTaskRequest) {
                val date = LocalDateTime.parse(request.separateAt, DateTimeFormatter.ISO_DATE_TIME)
                val task = TSeparationTask(
                    projectId = request.projectId,
                    repoName = request.repoName,
                    createdBy = "it",
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = "it",
                    lastModifiedDate = LocalDateTime.now(),
                    separationDate = LocalDateTime.of(date.toLocalDate(), LocalTime.MAX),
                    content = request.content,
                    type = request.type,
                )
                mongoTemplate.save(task)
            }

            override fun findTasks(
                state: String?,
                projectId: String?,
                repoName: String?,
                taskType: String?,
                pageRequest: PageRequest,
            ): Page<SeparationTask> = Pages.ofResponse(pageRequest, 0, emptyList())

            override fun reInitTaskState(taskId: String): Unit = unsupported()

            override fun repoSeparationCheck(projectId: String, repoName: String): Boolean = false

            override fun findProjectList(taskType: String?): List<String> = emptyList()

            override fun findSeparationCollectionList(projectId: String, taskType: String?): List<String> = emptyList()

            private fun unsupported(): Nothing = throw UnsupportedOperationException("node archive mongo IT")
        }
    }
}
