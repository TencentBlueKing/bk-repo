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

package com.tencent.bkrepo.scanner.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.scanner.component.ScannerPermissionCheckHandler
import com.tencent.bkrepo.scanner.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.scanner.component.manager.ScannerConverter
import com.tencent.bkrepo.scanner.dao.AbsSubScanTaskDao
import com.tencent.bkrepo.scanner.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.scanner.dao.FileScanResultDao
import com.tencent.bkrepo.scanner.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.scanner.dao.ScanPlanDao
import com.tencent.bkrepo.scanner.dao.ScanTaskDao
import com.tencent.bkrepo.scanner.dao.SubScanTaskDao
import com.tencent.bkrepo.scanner.exception.ScanTaskNotFoundException
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.scanner.pojo.request.FileScanResultDetailRequest
import com.tencent.bkrepo.scanner.pojo.request.FileScanResultOverviewRequest
import com.tencent.bkrepo.scanner.pojo.request.LoadResultArguments
import com.tencent.bkrepo.scanner.pojo.request.ScanTaskQuery
import com.tencent.bkrepo.scanner.pojo.request.SubtaskInfoRequest
import com.tencent.bkrepo.scanner.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.scanner.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.scanner.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.scanner.pojo.response.FileLicensesResultOverview
import com.tencent.bkrepo.scanner.pojo.response.FileScanResultDetail
import com.tencent.bkrepo.scanner.pojo.response.FileScanResultOverview
import com.tencent.bkrepo.scanner.pojo.response.SubtaskInfo
import com.tencent.bkrepo.scanner.pojo.response.SubtaskResultOverview
import com.tencent.bkrepo.scanner.service.ScanTaskService
import com.tencent.bkrepo.scanner.service.ScannerService
import com.tencent.bkrepo.scanner.utils.Converter
import com.tencent.bkrepo.scanner.utils.ScanLicenseConverter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class ScanTaskServiceImpl(
    private val permissionCheckHandler: ScannerPermissionCheckHandler,
    private val scannerService: ScannerService,
    private val scanPlanDao: ScanPlanDao,
    private val scanTaskDao: ScanTaskDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val nodeClient: NodeClient,
    private val repositoryClient: RepositoryClient,
    private val resultManagers: Map<String, ScanExecutorResultManager>,
    private val scannerConverters: Map<String, ScannerConverter>
) : ScanTaskService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun task(taskId: String): ScanTask {
        return scanTaskDao.findById(taskId)?.let { task ->
            if (task.projectId == null) {
                permissionCheckHandler.permissionManager.checkPrincipal(SecurityUtils.getUserId(), PrincipalType.ADMIN)
            } else {
                permissionCheckHandler.checkProjectPermission(task.projectId, PermissionAction.MANAGE)
            }
            val plan = task.planId?.let { scanPlanDao.get(it) }
            Converter.convert(task, plan)
        } ?: throw ScanTaskNotFoundException(taskId)
    }

    override fun tasks(scanTaskQuery: ScanTaskQuery, pageLimit: PageLimit): Page<ScanTask> {
        permissionCheckHandler.checkProjectPermission(scanTaskQuery.projectId, PermissionAction.MANAGE)
        val taskPage = scanTaskDao.find(scanTaskQuery, pageLimit)
        val records = taskPage.records.map { Converter.convert(it) }
        return Page(pageLimit.pageNumber, pageLimit.pageSize, taskPage.totalRecords, records)
    }

    override fun subtaskOverview(subtaskId: String): SubtaskResultOverview {
        return subtaskOverview(subtaskId, archiveSubScanTaskDao)
    }

    override fun subtasks(request: SubtaskInfoRequest): Page<SubtaskInfo> {
        if (request.parentScanTaskId == null) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
        }
        return subtasks(request, archiveSubScanTaskDao)
    }

    override fun planArtifactSubtaskPage(request: SubtaskInfoRequest): Page<SubtaskInfo> {
        if (request.id == null) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
        }
        return subtasks(request, planArtifactLatestSubScanTaskDao)
    }

    override fun planArtifactSubtaskOverview(subtaskId: String): SubtaskResultOverview {
        return subtaskOverview(subtaskId, planArtifactLatestSubScanTaskDao)
    }

    override fun resultOverview(request: FileScanResultOverviewRequest): List<FileScanResultOverview> {
        with(request) {
            val subScanTaskMap = subScanTaskDao
                .findByCredentialsKeyAndSha256List(credentialsKeyFiles)
                .associateBy { "${it.credentialsKey}:${it.sha256}" }

            return fileScanResultDao.findScannerResults(scanner, credentialsKeyFiles).map {
                val status = subScanTaskMap["${it.credentialsKey}:${it.sha256}"]?.status
                    ?: SubScanTaskStatus.SUCCESS.name
                // 只查询对应scanner的结果，此处必定不为null
                val scannerResult = it.scanResult[scanner]!!
                FileScanResultOverview(
                    status = status,
                    sha256 = it.sha256,
                    scanDate = scannerResult.startDateTime.format(DateTimeFormatter.ISO_DATE_TIME),
                    overview = scannerResult.overview
                )
            }
        }
    }

    override fun resultDetail(request: FileScanResultDetailRequest): FileScanResultDetail {
        with(request) {
            val node = artifactInfo!!.run {
                nodeClient.getNodeDetail(projectId, repoName, getArtifactFullPath())
            }.data!!
            val repo = repositoryClient.getRepoInfo(node.projectId, node.repoName).data!!

            val scanner = scannerService.get(scanner)
            val scanResultDetail = resultManagers[scanner.type]?.load(
                repo.storageCredentialsKey, node.sha256!!, scanner, arguments
            )
            val status = if (scanResultDetail == null) {
                subScanTaskDao.findByCredentialsAndSha256(repo.storageCredentialsKey, node.sha256!!)?.status
                    ?: SubScanTaskStatus.NEVER_SCANNED.name
            } else {
                SubScanTaskStatus.SUCCESS.name
            }
            return FileScanResultDetail(status, node.sha256!!, scanResultDetail)
        }
    }

    override fun resultDetail(request: ArtifactVulnerabilityRequest): Page<ArtifactVulnerabilityInfo> {
        return resultDetail(
            request, request.subScanTaskId!!, planArtifactLatestSubScanTaskDao,
            { converter, req -> converter.convertToLoadArguments(req) },
            { converter, report -> converter.convertCveResult(report) }
        ) ?: Pages.buildPage(emptyList(), request.pageSize, request.pageNumber)
    }

    override fun archiveSubtaskResultDetail(request: ArtifactVulnerabilityRequest): Page<ArtifactVulnerabilityInfo> {
        return resultDetail(
            request, request.subScanTaskId!!, archiveSubScanTaskDao,
            { converter, req -> converter.convertToLoadArguments(req) },
            { converter, report -> converter.convertCveResult(report) }
        ) ?: Pages.buildPage(emptyList(), request.pageSize, request.pageNumber)
    }

    private fun subtaskOverview(subtaskId: String, subtaskDao: AbsSubScanTaskDao<*>): SubtaskResultOverview {
        val subtask = subtaskDao.findById(subtaskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, subtaskId)
        try {
            permissionCheckHandler.checkSubtaskPermission(subtask, PermissionAction.READ)
        } catch (e: RepoNotFoundException) {
            logger.info("Failed to checkSubtaskPermission: ", e)
            permissionCheckHandler.checkProjectPermission(subtask.projectId, PermissionAction.MANAGE)
        }
        return Converter.convert(subtask)
    }

    private fun subtasks(request: SubtaskInfoRequest, subtaskDao: AbsSubScanTaskDao<*>): Page<SubtaskInfo> {
        with(request) {
            val page = subtaskDao.pageBy(request)
            return Pages.ofResponse(
                Pages.ofRequest(pageNumber, pageSize),
                page.totalRecords,
                page.records.map { Converter.convertToSubtaskInfo(it) }
            )
        }
    }

    override fun resultDetail(request: ArtifactLicensesDetailRequest): Page<FileLicensesResultDetail> {
        return resultDetail(
            request, request.subScanTaskId!!, planArtifactLatestSubScanTaskDao,
            { converter, req -> converter.convertToLoadArguments(req) },
            { converter, report -> converter.convertLicenseResult(report) }
        ) ?: Pages.buildPage(emptyList(), request.pageSize, request.pageNumber)
    }

    override fun planLicensesArtifact(projectId: String, subScanTaskId: String): FileLicensesResultOverview {
        return planLicensesArtifact(subScanTaskId, planArtifactLatestSubScanTaskDao)
    }

    override fun archiveSubtaskResultDetail(request: ArtifactLicensesDetailRequest): Page<FileLicensesResultDetail> {
        return resultDetail(
            request, request.subScanTaskId!!, archiveSubScanTaskDao,
            { converter, req -> converter.convertToLoadArguments(req) },
            { converter, report -> converter.convertLicenseResult(report) }
        ) ?: Pages.buildPage(emptyList(), request.pageSize, request.pageNumber)
    }

    override fun subtaskLicenseOverview(subtaskId: String): FileLicensesResultOverview {
        return planLicensesArtifact(subtaskId, archiveSubScanTaskDao)
    }

    private fun <Req, Res> resultDetail(
        request: Req,
        subtaskId: String,
        subScanTaskDao: AbsSubScanTaskDao<*>,
        convertToArgs: (converter: ScannerConverter, req: Req) -> LoadResultArguments,
        convertToRes: (converter: ScannerConverter, report: Any) -> Page<Res>
    ): Page<Res>? {
        val subtask = subScanTaskDao.findById(subtaskId)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, subtaskId)

        try {
            permissionCheckHandler.checkSubtaskPermission(subtask, PermissionAction.READ)
        } catch (e: RepoNotFoundException) {
            logger.info("Failed to checkSubtaskPermission: ", e)
            permissionCheckHandler.checkProjectPermission(subtask.projectId, PermissionAction.MANAGE)
        }

        val scanner = scannerService.get(subtask.scanner)
        val scannerConverter = scannerConverters[ScannerConverter.name(scanner.type)] ?: return null
        val arguments = convertToArgs(scannerConverter, request)
        val scanResultManager = resultManagers[subtask.scannerType]
        return scanResultManager
            ?.load(subtask.credentialsKey, subtask.sha256, scanner, arguments)
            ?.let { convertToRes(scannerConverter, it) }
    }

    private fun planLicensesArtifact(
        subScanTaskId: String,
        subtaskDao: AbsSubScanTaskDao<*>
    ): FileLicensesResultOverview {
        val subtask = subtaskDao.findById(subScanTaskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, subScanTaskId)
        permissionCheckHandler.checkSubtaskPermission(subtask, PermissionAction.READ)
        return ScanLicenseConverter.convert(subtask)
    }
}
