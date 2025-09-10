/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.separation.service.impl

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.mongo.dao.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.job.RESTORE
import com.tencent.bkrepo.job.SEPARATE
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils.Companion.SEPARATION_COLLECTION_NAME_PREFIX
import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.dao.SeparationFailedRecordDao
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import com.tencent.bkrepo.job.separation.model.TSeparationTask
import com.tencent.bkrepo.job.separation.pojo.SeparationArtifactType
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTask
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTask.Companion.toDto
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskRequest
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskState
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class SeparationTaskServiceImpl(
    private val dataSeparationConfig: DataSeparationConfig,
    private val repositoryService: RepositoryService,
    private val separationTaskDao: SeparationTaskDao,
    private val separationFailedRecordDao: SeparationFailedRecordDao,
    private val mongoTemplate: MongoTemplate,
) : SeparationTaskService {
    override fun createSeparationTask(request: SeparationTaskRequest) {
        with(request) {
            val repo = getRepoInfo(projectId, repoName)
            contentCheck(request, repo)
            when (type) {
                SEPARATE -> {
                    validateSeparateTaskParams(request)
                    val task = buildSeparationTask(request)
                    createTask(task)
                }

                RESTORE -> {
                    restoreTaskCheck(request.projectId, request.repoName)
                    createRestoreTask(request)
                }

                else -> {
                    logger.warn("unsupported task type $type")
                    throw BadRequestException(
                        CommonMessageCode.PARAMETER_INVALID, SeparationTaskRequest::type.name
                    )
                }
            }

        }
    }

    override fun findDistinctSeparationDate(projectId: String?, repoName: String?): Set<LocalDateTime> {
        val result = mutableSetOf<LocalDateTime>()
        val criteria = Criteria.where(TSeparationTask::type.name).isEqualTo(SEPARATE)
        projectId?.apply { criteria.and(TSeparationTask::projectId.name).isEqualTo(projectId) }
        repoName?.apply { criteria.and(TSeparationTask::repoName.name).isEqualTo(repoName) }
        val query = Query(criteria)
        val dateRecords = mongoTemplate.find(query, TSeparationTask::class.java)
        dateRecords.forEach {
            result.add(it.separationDate)
        }
        return result
    }

    override fun findTasks(
        state: String?,
        projectId: String?,
        repoName: String?,
        taskType: String?,
        pageRequest: PageRequest,
    ): Page<SeparationTask> {
        val count = separationTaskDao.count(state, projectId, repoName, taskType)
        val records = separationTaskDao.find(state, projectId, repoName, taskType, pageRequest).map { it.toDto() }
        return Pages.ofResponse(pageRequest, count, records)
    }

    override fun reInitTaskState(taskId: String) {
        separationTaskDao.findById(taskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, taskId)
        separationTaskDao.updateState(taskId, SeparationTaskState.PENDING)
    }

    override fun repoSeparationCheck(projectId: String, repoName: String): Boolean {
        val exist = separationTaskDao.exist(projectId, repoName, SeparationTaskState.FINISHED.name)
        val failedExist = separationFailedRecordDao.exist(projectId, repoName)
        return exist || failedExist
    }

    override fun findProjectList(): List<String> {
        val criteria = Criteria.where(TSeparationTask::projectId.name).exists(true)
            .and(TSeparationTask::type.name).isEqualTo(SEPARATE)
        val query = Query(criteria)
        return mongoTemplate.findDistinct(
            query, TSeparationTask::projectId.name, TSeparationTask::class.java, String::class.java
        )
    }

    override fun findSeparationCollectionList(projectId: String): List<String> {
        val projectRepoKey = "$projectId/*"
        if (!matchesConfigRepos(projectRepoKey, dataSeparationConfig.specialSeparateRepos)) {
            return emptyList()
        }

        val result = mutableListOf<String>()
        findDistinctSeparationDate(projectId).forEach { date ->
            val separationNodeCollection = SEPARATION_COLLECTION_NAME_PREFIX.plus(
                MonthRangeShardingUtils.shardingSequenceFor(date, 1)
            )
            result.add(separationNodeCollection)
        }
        return result
    }

    private fun restoreTaskCheck(
        projectId: String,
        repoName: String,
    ) {
        val projectRepoKey = "${projectId}/${repoName}"
        if (!matchesConfigRepos(projectRepoKey, dataSeparationConfig.specialRestoreRepos)) {
            throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID,
                projectRepoKey
            )
        }
    }

    private fun createRestoreTask(request: SeparationTaskRequest) {
        with(request) {
            if (separateAt.isNullOrEmpty()) {
                val separatedDates = findDistinctSeparationDate(projectId, repoName)
                if (separatedDates.isEmpty()) {
                    logger.warn("no cold data has been stored in $projectId|$repoName")
                    throw BadRequestException(
                        CommonMessageCode.PARAMETER_INVALID, SeparationTaskRequest::type.name
                    )
                }
                separatedDates.forEach {
                    val task = buildSeparationTask(request, it)
                    createTask(task)
                }
            } else {
                val date = LocalDateTime.parse(separateAt, DateTimeFormatter.ISO_DATE_TIME)
                val task = buildSeparationTask(request, date)
                createTask(task)
            }
        }
    }

    private fun createTask(task: TSeparationTask) {
        val exist = separationTaskDao.exist(
            projectId = task.projectId,
            repoName = task.repoName,
            state = SeparationTaskState.FINISHED.name,
            content = task.content,
            type = task.type,
            separationDate = task.separationDate,
            overwrite = task.overwrite
        )
        if (exist) {
            logger.info("$task is existed, ignore")
            return
        }
        separationTaskDao.save(task)
    }

    private fun getRepoInfo(projectId: String, repoName: String): RepositoryDetail {
        val repo = repositoryService.getRepoDetail(projectId, repoName)
            ?: run {
                logger.warn("Repo [$projectId|$repoName] not exist.")
                throw NotFoundException(
                    CommonMessageCode.RESOURCE_NOT_FOUND,
                    "repo: $projectId|$repoName"
                )
            }
        return repo
    }

    private fun validateSeparateTaskParams(request: SeparationTaskRequest) {
        with(request) {
            if (separateAt == null) {
                logger.warn("Separation date [$separateAt] is null!")
                throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID,
                    SeparationTaskRequest::separateAt.name
                )
            }

            val projectRepoKey = "$projectId/$repoName"
            if (!matchesConfigRepos(projectRepoKey, dataSeparationConfig.specialSeparateRepos)) {
                throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID,
                    projectRepoKey
                )
            }

            val separateDate = try {
                LocalDateTime.parse(separateAt, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e: DateTimeParseException) {
                logger.warn("Separation date $separateAt parse error")
                throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID,
                    SeparationTaskRequest::separateAt.name
                )
            }
            if (LocalDateTime.now().minusDays(dataSeparationConfig.keepDays.toDays()).isAfter(separateDate)) {
                return
            }

            logger.warn("Separation date [$separateAt] is illegal!")
            throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID,
                SeparationTaskRequest::separateAt.name
            )
        }
    }

    private fun contentCheck(request: SeparationTaskRequest, repo: RepositoryDetail) {
        val separationArtifactType = when (repo.type) {
            RepositoryType.GENERIC -> SeparationArtifactType.NODE
            else -> SeparationArtifactType.PACKAGE
        }
        if (!request.content.packages.isNullOrEmpty() && separationArtifactType == SeparationArtifactType.NODE) {
            logger.warn("Separation content [${request.content}] is illegal!")
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID)
        }
        if (!request.content.paths.isNullOrEmpty() && separationArtifactType == SeparationArtifactType.PACKAGE) {
            logger.warn("Separation content [${request.content}] is illegal!")
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID)
        }
    }

    private fun buildSeparationTask(
        request: SeparationTaskRequest,
        restoreDate: LocalDateTime? = null,
        userId: String = SecurityUtils.getUserId(),
    ): TSeparationTask {
        with(request) {
            val userId = userId
            val date = restoreDate ?: LocalDateTime.parse(separateAt, DateTimeFormatter.ISO_DATE_TIME)
            val separateDate = LocalDateTime.of(date.toLocalDate(), LocalTime.MAX)
            return TSeparationTask(
                projectId = projectId,
                repoName = repoName,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                separationDate = separateDate,
                content = content,
                type = type,
                overwrite = overwrite
            )
        }
    }

    /**
     * 检查项目仓库键是否匹配配置的仓库列表
     */
    private fun matchesConfigRepos(projectRepoKey: String, configRepos: List<String>): Boolean {
        return configRepos.any { configRepo ->
            val regex = Regex(configRepo.replace("*", ".*"))
            regex.matches(projectRepoKey)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SeparationTaskServiceImpl::class.java)
    }
}
