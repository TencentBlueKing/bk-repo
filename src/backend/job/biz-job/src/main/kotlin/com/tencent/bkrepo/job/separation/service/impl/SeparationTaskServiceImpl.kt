/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.job.RESTORE
import com.tencent.bkrepo.job.SEPARATE
import com.tencent.bkrepo.job.SEPARATION_TASK_COLLECTION_NAME
import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import com.tencent.bkrepo.job.separation.model.TSeparationTask
import com.tencent.bkrepo.job.separation.pojo.SeparationArtifactType
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskRequest
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SeparationTaskServiceImpl(
    private val dataSeparationConfig: DataSeparationConfig,
    private val repositoryClient: RepositoryClient,
    private val separationTaskDao: SeparationTaskDao,
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
                    separationTaskDao.save(task)
                }
                RESTORE -> {
                    val separatedDates = findDistinctSeparationDate(projectId, repoName)
                    if (separatedDates.isEmpty()) {
                        logger.warn("no cold data has been stored in $projectId|$repoName")
                        throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, SeparationTaskRequest::type.name)
                    }
                    separatedDates.forEach {
                        request.separationDate = it
                        val task = buildSeparationTask(request)
                        separationTaskDao.save(task)
                    }
                }
                else -> {
                    logger.warn("unsupported task type $type")
                    throw BadRequestException(CommonMessageCode.PARAMETER_INVALID)
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
        val dateRecords = mongoTemplate.findDistinct(
            query, TSeparationTask::separationDate.name, SEPARATION_TASK_COLLECTION_NAME, LocalDateTime::class.java
        )
        result.addAll(dateRecords)
        return result
    }

    private fun getRepoInfo(projectId: String, repoName: String): RepositoryDetail {
        val repo = repositoryClient.getRepoDetail(projectId, repoName).data
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
            if (separationDate == null) {
                logger.warn("Separation date [$separationDate] is illegal!")
                throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, SeparationTaskRequest::separationDate.name)
            }
            if (LocalDateTime.now().minusDays(dataSeparationConfig.keepDays.toDays()).isAfter(separationDate)) {
                return
            }
            val projectRepoKey = "$projectId/$repoName"
            dataSeparationConfig.specialRepos.forEach {
                val regex = Regex(it.replace("*", ".*"))
                if (regex.matches(projectRepoKey)) {
                    return
                }
            }
            logger.warn("Separation date [$separationDate] is illegal!")
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, SeparationTaskRequest::separationDate.name)
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

    private fun buildSeparationTask(request: SeparationTaskRequest): TSeparationTask {
        with(request) {
            val userId = SecurityUtils.getUserId()
            return TSeparationTask(
                projectId = projectId,
                repoName = repoName,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                separationDate = separationDate!!.toLocalDate().atStartOfDay(),
                content = content,
                type = type,
                overwrite = overwrite
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SeparationTaskServiceImpl::class.java)
    }
}
