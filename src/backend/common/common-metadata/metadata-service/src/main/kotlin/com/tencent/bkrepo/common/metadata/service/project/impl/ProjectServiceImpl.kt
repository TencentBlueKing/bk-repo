/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.constant.TOTAL_RECORDS_INFINITY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.properties.EnableMultiTenantProperties
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.project.ProjectDao
import com.tencent.bkrepo.common.metadata.dao.project.ProjectMetricsDao
import com.tencent.bkrepo.common.metadata.listener.ResourcePermissionListener
import com.tencent.bkrepo.common.metadata.model.TProject
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
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
import com.tencent.bkrepo.common.service.cluster.condition.DefaultCondition
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectListOption
import com.tencent.bkrepo.repository.pojo.project.ProjectMetricsInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectSearchOption
import com.tencent.bkrepo.repository.pojo.project.ProjectUpdateRequest
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
@Conditional(SyncCondition::class, DefaultCondition::class)
class ProjectServiceImpl(
    private val projectDao: ProjectDao,
    private val servicePermissionClient: ServicePermissionClient,
    private val projectMetricsDao: ProjectMetricsDao,
    private val serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient,
    private val storageCredentialService: StorageCredentialService,
    private val repositoryProperties: RepositoryProperties,
    private val enableMultiTenant: EnableMultiTenantProperties
) : ProjectService {

    @Autowired
    @Lazy
    private lateinit var resourcePermissionListener: ResourcePermissionListener

    override fun getProjectInfo(name: String): ProjectInfo? {
        return convert(projectDao.findByName(name))
    }

    override fun getProjectInfoByDisplayName(displayName: String): ProjectInfo? {
        return convert(projectDao.findByDisplayName(displayName))
    }

    override fun listProject(): List<ProjectInfo> {
        val query = buildListQuery()
        return projectDao.find(query).map { convert(it)!! }
    }

    override fun searchProject(option: ProjectSearchOption): Page<ProjectInfo> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = buildSearchQuery(pageRequest)
            val records = projectDao.find(query)
            return Pages.ofResponse(pageRequest, TOTAL_RECORDS_INFINITY, records.map { convert(it)!! })
        }
    }

    override fun listPermissionProject(userId: String, option: ProjectListOption?): List<ProjectInfo> {
        // 校验租户信息
        if (enableMultiTenant.enabled) {
            validateTenantId()
        }
        var names = servicePermissionClient.listPermissionProject(userId).data.orEmpty()
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
        val existProjectMap = serviceBkiamV3ResourceClient.getExistRbacDefaultGroupProjectIds(projectIdList).data
        return projectList.map {
            val exist = existProjectMap?.get(it.name) ?: false
            convert(it, exist)!!
        }
    }

    override fun isProjectEnabled(name: String): Boolean {
        if (!repositoryProperties.returnEnabled) return true
        val projectInfo = projectDao.findByName(name)
            ?: throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, name)
        return ProjectServiceHelper.isProjectEnabled(projectInfo)
    }

    override fun rangeQuery(request: ProjectRangeQueryRequest): Page<ProjectInfo?> {
        val limit = request.limit
        val skip = request.offset
        val query = buildRangeQuery(request)
        val totalCount = projectDao.count(query)
        val records = projectDao.find(query.limit(limit).skip(skip)).map { convert(it) }
        return Page(0, limit, totalCount, records)
    }

    override fun checkExist(name: String): Boolean {
        return projectDao.findByName(name) != null
    }

    override fun createProject(request: ProjectCreateRequest): ProjectInfo {
        val name = request.name
        validateParameter(request)

        if (checkExist(name)) {
            throw ErrorCodeException(ArtifactMessageCode.PROJECT_EXISTED, name)
        }
        // 校验租户信息
        if (enableMultiTenant.enabled) {
            logger.info("check tenant")
            validateTenantId()
        }
        val project = request.buildProject(ProjectServiceHelper.getTenantId())
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

    /**
     * true:资源已存在, false: 资源不存在
     */
    override fun checkProjectExist(name: String?, displayName: String?): Boolean {
        val nameResult = name?.let { getProjectInfo(it) != null } ?: false
        val displayNameResult = displayName?.let { getProjectInfoByDisplayName(it) != null } ?: false
        return nameResult || displayNameResult
    }

    override fun getProjectMetricsInfo(name: String): ProjectMetricsInfo? {
        val today = LocalDate.now().atStartOfDay()
        var metrics = projectMetricsDao.findByProjectIdAndCreatedDate(name, today)
        // 可能当天任务还没有统计出来，则查询前一天的数据
        if (metrics == null) {
            val yesterday = today.minusDays(1)
            metrics = projectMetricsDao.findByProjectIdAndCreatedDate(name, yesterday)
        }
        return convert(metrics)
    }

    override fun updateProject(name: String, request: ProjectUpdateRequest): Boolean {
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

    private fun validateParameter(request: ProjectCreateRequest) {
        with(request) {
            validate()
            credentialsKey?.let { checkCredentialsKey(it) }
        }
    }

    private fun validateTenantId() {
        if (ProjectServiceHelper.getTenantId().isNullOrEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "tenantId")
        }
    }

    private fun checkCredentialsKey(key: String) {
        storageCredentialService.findByKey(key) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, key)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectServiceImpl::class.java)
    }
}
