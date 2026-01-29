/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.project.impl

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.constant.TOTAL_RECORDS_INFINITY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.client.RAuthClient
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.project.RProjectDao
import com.tencent.bkrepo.common.metadata.dao.project.RProjectMetricsDao
import com.tencent.bkrepo.common.metadata.listener.RResourcePermissionListener
import com.tencent.bkrepo.common.metadata.model.TProject
import com.tencent.bkrepo.common.metadata.service.project.RProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RStorageCredentialService
import com.tencent.bkrepo.common.metadata.util.ProjectEventFactory.buildCreatedEvent
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.DISPLAY_NAME_LENGTH_MAX
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.DISPLAY_NAME_LENGTH_MIN
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.buildListQuery
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.buildProject
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.buildRangeQuery
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.buildSearchQuery
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.buildUpdate
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.convert
import com.tencent.bkrepo.common.metadata.util.ProjectServiceHelper.validate
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectListOption
import com.tencent.bkrepo.repository.pojo.project.ProjectMetricsInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectSearchOption
import com.tencent.bkrepo.repository.pojo.project.ProjectUpdateRequest
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 仓库服务实现类
 */
@Service
@Conditional(ReactiveCondition::class)
class RProjectServiceImpl(
    private val projectDao: RProjectDao,
    private val projectMetricsDao: RProjectMetricsDao,
    private val storageCredentialService: RStorageCredentialService,
    private val rAuthClient: RAuthClient,
    private val repositoryProperties: RepositoryProperties,
) : RProjectService {

    @Autowired
    @Lazy
    private lateinit var resourcePermissionListener: RResourcePermissionListener

    override suspend fun getProjectInfo(name: String): ProjectInfo? {
        return convert(projectDao.findByName(name))
    }

    override suspend fun getProjectInfoByDisplayName(displayName: String): ProjectInfo? {
        return convert(projectDao.findByDisplayName(displayName))
    }

    override suspend fun listProject(): List<ProjectInfo> {
        val query = buildListQuery()
        return projectDao.find(query).map { convert(it)!! }
    }

    override suspend fun searchProject(option: ProjectSearchOption): Page<ProjectInfo> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = buildSearchQuery(pageRequest)

            val records = projectDao.find(query)
            return Pages.ofResponse(pageRequest, TOTAL_RECORDS_INFINITY, records.map { convert(it)!! })
        }
    }

    override suspend fun listPermissionProject(userId: String, option: ProjectListOption?): List<ProjectInfo> {
        var names = rAuthClient.listPermissionProject(userId).awaitSingle().data.orEmpty()
        option?.names?.let { names = names.intersect(option.names!!).toList() }
        val query = buildListQuery(names, option)
        val projectList = if (option?.pageNumber == null && option?.pageSize == null) {
            projectDao.find(query)
        } else {
            val pageRequest = Pages.ofRequest(
                option.pageNumber ?: DEFAULT_PAGE_NUMBER,
                option.pageSize ?: DEFAULT_PAGE_SIZE
            )
            projectDao.find(query.with(pageRequest))
        }
        val projectIdList = projectList.map { it.name }
        val existProjectMap = rAuthClient.getExistRbacDefaultGroupProjectIds(projectIdList).awaitSingle().data
        return projectList.map {
            val exist = existProjectMap?.get(it.name) ?: false
            convert(it, exist)!!
        }
    }

    override suspend fun isProjectEnabled(name: String): Boolean {
        if (!repositoryProperties.returnEnabled) return true
        val projectInfo = projectDao.findByName(name)
            ?: throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, name)
        return ProjectServiceHelper.isProjectEnabled(projectInfo)
    }

    override suspend fun rangeQuery(request: ProjectRangeQueryRequest): Page<ProjectInfo?> {
        val limit = request.limit
        val skip = request.offset
        val query = buildRangeQuery(request)
        val totalCount = projectDao.count(query)
        val records = projectDao.find(query.limit(limit).skip(skip)).map { convert(it) }
        return Page(0, limit, totalCount, records)
    }

    override suspend fun checkExist(name: String): Boolean {
        return projectDao.findByName(name) != null
    }

    override suspend fun createProject(request: ProjectCreateRequest): ProjectInfo {
        with(request) {
            validateParameter(this)
            if (checkExist(name)) {
                throw ErrorCodeException(ArtifactMessageCode.PROJECT_EXISTED, name)
            }
            // TODO ,多租户暂时不涉及
            val project = buildProject(null, false)
            return try {
                projectDao.insert(project)
                resourcePermissionListener.handle(buildCreatedEvent(request))
                logger.info("Create project [$name] success.")
                convert(project)!!
            } catch (exception: DuplicateKeyException) {
                logger.warn("Insert project[$name] error: [${exception.message}]")
                getProjectInfo(name)!!
            }
        }
    }

    /**
     * true:资源已存在, false: 资源不存在
     */
    override suspend fun checkProjectExist(name: String?, displayName: String?): Boolean {
        val nameResult = name?.let { getProjectInfo(it) != null } ?: false
        val displayNameResult = displayName?.let { getProjectInfoByDisplayName(it) != null } ?: false
        return nameResult || displayNameResult
    }

    override suspend fun getProjectMetricsInfo(name: String): ProjectMetricsInfo? {
        val today = LocalDate.now().atStartOfDay()
        var metrics = projectMetricsDao.findByProjectIdAndCreatedDate(name, today)
        // 可能当天任务还没有统计出来，则查询前一天的数据
        if (metrics == null) {
            val yesterday = today.minusDays(1)
            metrics = projectMetricsDao.findByProjectIdAndCreatedDate(name, yesterday)
        }
        return convert(metrics)
    }

    override suspend fun updateProject(name: String, request: ProjectUpdateRequest): Boolean {
        if (!checkExist(name)) {
            throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, name)
        }
        request.displayName?.let {
            if (it.length < DISPLAY_NAME_LENGTH_MIN || it.length > DISPLAY_NAME_LENGTH_MAX) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::displayName.name)
            }
        }
        request.credentialsKey?.let { checkCredentialsKey(it) }
        val query = Query.query(Criteria.where(TProject::name.name).`is`(name))
        val update = buildUpdate(request)
        val updateResult = projectDao.updateFirst(query, update)
        return if (updateResult.modifiedCount == 1L) {
            logger.info("Update project [$name] success.")
            true
        } else {
            logger.error("Update project fail : $request")
            false
        }
    }

    private suspend fun validateParameter(request: ProjectCreateRequest) {
        with(request) {
            validate()
            credentialsKey?.let { checkCredentialsKey(it) }
        }
    }

    private suspend fun checkCredentialsKey(key: String) {
        storageCredentialService.findByKey(key) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, key)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RProjectServiceImpl::class.java)
    }
}
