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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.DEFAULT_PROJECT_SCAN_PRIORITY
import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.DEFAULT_SCAN_TASK_COUNT_LIMIT
import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.DEFAULT_SUB_SCAN_TASK_COUNT_LIMIT
import com.tencent.bkrepo.analyst.dao.ProjectScanConfigurationDao
import com.tencent.bkrepo.analyst.model.TProjectScanConfiguration
import com.tencent.bkrepo.analyst.model.TProjectScanConfiguration.Companion.GLOBAL_PROJECT_ID
import com.tencent.bkrepo.analyst.pojo.DispatcherConfiguration
import com.tencent.bkrepo.analyst.pojo.ProjectScanConfiguration
import com.tencent.bkrepo.analyst.pojo.request.ProjectScanConfigurationPageRequest
import com.tencent.bkrepo.analyst.service.ProjectScanConfigurationService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.utils.Converter
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ProjectScanConfigurationServiceImpl(
    private val projectScanConfigurationDao: ProjectScanConfigurationDao,
    private val scannerService: ScannerService
) : ProjectScanConfigurationService {
    override fun create(request: ProjectScanConfiguration): ProjectScanConfiguration {
        check(request)
        with(request) {
            if (projectScanConfigurationDao.existsByProjectId(projectId)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, projectId)
            }

            val userId = SecurityUtils.getUserId()
            val now = LocalDateTime.now()

            val priority = priority ?: DEFAULT_PROJECT_SCAN_PRIORITY
            val scanTaskCountLimit = scanTaskCountLimit ?: DEFAULT_SCAN_TASK_COUNT_LIMIT
            val subScanTaskCountLimit = subScanTaskCountLimit
                ?: DEFAULT_SUB_SCAN_TASK_COUNT_LIMIT
            val configuration = TProjectScanConfiguration(
                createdBy = userId,
                createdDate = now,
                lastModifiedBy = userId,
                lastModifiedDate = now,
                projectId = projectId,
                priority = priority,
                scanTaskCountLimit = scanTaskCountLimit,
                subScanTaskCountLimit = subScanTaskCountLimit,
                autoScanConfiguration = autoScanConfiguration ?: emptyMap(),
                dispatcherConfiguration = dispatcherConfiguration ?: emptyList()
            )
            return Converter.convert(projectScanConfigurationDao.insert(configuration))
        }
    }

    override fun delete(projectId: String) {
        if (!projectScanConfigurationDao.deleteByProjectId(projectId)) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId)
        }
    }

    override fun update(request: ProjectScanConfiguration): ProjectScanConfiguration {
        check(request)
        with(request) {
            val oldConfiguration = projectScanConfigurationDao.findByProjectId(projectId)
                ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)
            val lastModifiedBy = SecurityUtils.getUserId()
            val lastModifiedDate = LocalDateTime.now()
            val newConfiguration = oldConfiguration.copy(
                priority = priority ?: oldConfiguration.priority,
                scanTaskCountLimit = scanTaskCountLimit ?: oldConfiguration.scanTaskCountLimit,
                subScanTaskCountLimit = subScanTaskCountLimit ?: oldConfiguration.subScanTaskCountLimit,
                autoScanConfiguration = autoScanConfiguration ?: oldConfiguration.autoScanConfiguration,
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate,
                dispatcherConfiguration = dispatcherConfiguration ?: oldConfiguration.dispatcherConfiguration
            )
            return Converter.convert(projectScanConfigurationDao.save(newConfiguration))
        }
    }

    override fun page(request: ProjectScanConfigurationPageRequest): Page<ProjectScanConfiguration> {
        with(request) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val page = projectScanConfigurationDao.page(projectId, pageRequest)
            return Pages.ofResponse(
                Pages.ofRequest(pageNumber, pageSize),
                page.totalRecords,
                page.records.map { Converter.convert(it) }
            )
        }
    }

    override fun get(projectId: String): ProjectScanConfiguration {
        return projectScanConfigurationDao.findByProjectId(projectId)
            ?.let { Converter.convert(it) }
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)
    }

    override fun findProjectOrGlobalScanConfiguration(projectId: String?): ProjectScanConfiguration? {
        val configuration = projectId?.let {
            projectScanConfigurationDao.findByProjectId(it)
        } ?: projectScanConfigurationDao.findByProjectId(GLOBAL_PROJECT_ID)
        return configuration?.let { Converter.convert(it) }
    }

    private fun check(projectScanConfiguration: ProjectScanConfiguration) {
        with(projectScanConfiguration) {
            if (dispatcherConfiguration?.isNotEmpty() == true)  {
                checkDispatcherConfiguration(dispatcherConfiguration!!)
            }
        }
    }

    private fun checkDispatcherConfiguration(dispatcherConfigurations: List<DispatcherConfiguration>) {
        val scanners = scannerService.find(dispatcherConfigurations.map { it.scanner })
        dispatcherConfigurations.forEach { dispatcherConfiguration ->
            val scanner = scanners.firstOrNull { it.name == dispatcherConfiguration.scanner }
                ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, dispatcherConfiguration.scanner)
            if (dispatcherConfiguration.dispatcher !in scanner.supportDispatchers) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, dispatcherConfiguration.dispatcher)
            }
        }
    }
}
