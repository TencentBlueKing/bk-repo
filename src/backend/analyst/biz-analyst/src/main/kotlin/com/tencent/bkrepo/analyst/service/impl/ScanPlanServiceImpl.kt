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

import com.tencent.bkrepo.analyst.component.ScannerPermissionCheckHandler
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScannerDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.message.ScannerMessageCode
import com.tencent.bkrepo.analyst.model.TScanPlan
import com.tencent.bkrepo.analyst.pojo.ScanPlan
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.request.ArtifactPlanRelationRequest
import com.tencent.bkrepo.analyst.pojo.request.PlanCountRequest
import com.tencent.bkrepo.analyst.pojo.request.UpdateScanPlanRequest
import com.tencent.bkrepo.analyst.pojo.response.ArtifactPlanRelation
import com.tencent.bkrepo.analyst.pojo.response.ArtifactPlanRelations
import com.tencent.bkrepo.analyst.pojo.response.ScanLicensePlanInfo
import com.tencent.bkrepo.analyst.pojo.response.ScanPlanInfo
import com.tencent.bkrepo.analyst.service.ScanPlanService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.utils.Request
import com.tencent.bkrepo.analyst.utils.RuleConverter
import com.tencent.bkrepo.analyst.utils.RuleUtil
import com.tencent.bkrepo.analyst.utils.ScanLicenseConverter
import com.tencent.bkrepo.analyst.utils.ScanParamUtil
import com.tencent.bkrepo.analyst.utils.ScanPlanConverter
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.api.PackageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScanPlanServiceImpl(
    private val packageClient: PackageClient,
    private val scanPlanDao: ScanPlanDao,
    private val scanTaskDao: ScanTaskDao,
    private val scannerDao: ScannerDao,
    private val scannerService: ScannerService,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val permissionCheckHandler: ScannerPermissionCheckHandler,
    private val subScanTaskDao: SubScanTaskDao
) : ScanPlanService {
    override fun create(request: ScanPlan): ScanPlan {
        val operator = SecurityUtils.getUserId()
        logger.info("userId:$operator, create scanPlan[${request.name}]")
        with(request) {
            if (name.isNullOrEmpty() || name!!.length > PLAN_NAME_LENGTH_MAX) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "name cannot be empty or length > 32")
            }

            if (!RepositoryType.values().map { it.name }.contains(type)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "invalid scan plan type[$type]")
            }

            if (scanTypes.isNullOrEmpty()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "scanTypes must not be empty")
            }

            if (scanPlanDao.existsByProjectIdAndName(projectId!!, name!!)) {
                logger.error("scan plan [$name] is exist.")
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name.toString())
            }

            if (!scannerDao.existsByName(scanner!!)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, scanner!!)
            }

            val now = LocalDateTime.now()
            val tScanPlan = TScanPlan(
                projectId = projectId!!,
                name = name!!,
                type = type!!,
                scanTypes = scanTypes!!,
                description = description ?: "",
                scanner = scanner!!,
                scanOnNewArtifact = scanOnNewArtifact ?: false,
                repoNames = repoNames ?: emptyList(),
                rule = rule!!.toJsonString(),
                createdBy = operator,
                createdDate = now,
                lastModifiedBy = operator,
                lastModifiedDate = now,
                readOnly = readOnly ?: false
            )
            return ScanPlanConverter.convert(scanPlanDao.insert(tScanPlan))
        }
    }

    override fun list(projectId: String, type: String?, fileNameExt: String?): List<ScanPlan> {
        var scanPlans = scanPlanDao.list(projectId, type)
        if (type == RepositoryType.GENERIC.name && fileNameExt != null) {
            // 筛选支持指定文件名后缀的扫描方案
            val scannerNames = scanPlans.map { it.scanner }
            val scanners = scannerService.find(scannerNames).associateBy { it.name }
            scanPlans = scanPlans.filter {
                val scanner = scanners[it.scanner]
                // 扫描器为null表示扫描器已被删除，扫描器被删除后不再列出其对应的扫描方案
                scanner != null && (scanner.supportFileNameExt.isEmpty() || fileNameExt in scanner.supportFileNameExt)
            }
        }
        return scanPlans.map { ScanPlanConverter.convert(it) }
    }

    override fun page(
        projectId: String,
        type: String?,
        planNameContains: String?,
        pageLimit: PageLimit
    ): Page<ScanPlanInfo> {
        val page = scanPlanDao.page(projectId, type, planNameContains, pageLimit)

        val scanTaskIds = page.records.filter { it.latestScanTaskId != null }.map { it.latestScanTaskId!! }
        val scanTaskMap = scanTaskDao.findByIds(scanTaskIds).associateBy { it.id }

        val planArtifactCountMap = planArtifactLatestSubScanTaskDao.planArtifactCount(page.records.map { it.id!! })
        val scanningPlan = subScanTaskDao.findScanning(projectId).map { it.planId }.distinct()

        val scanPlanInfoList = page.records.map {
            ScanPlanConverter.convert(
                it,
                scanTaskMap[it.latestScanTaskId],
                planArtifactCountMap[it.id!!] ?: 0L,
                scanningPlan.contains(it.id)
            )
        }

        return Page(page.pageNumber, page.pageSize, page.totalRecords, scanPlanInfoList)
    }

    override fun find(projectId: String, id: String): ScanPlan? {
        return scanPlanDao.find(projectId, id)?.let { ScanPlanConverter.convert(it) }
    }

    override fun findByName(projectId: String, type: String, name: String): ScanPlan? {
        return scanPlanDao.find(projectId, type, name)?.let { ScanPlanConverter.convert(it) }
    }

    override fun getOrCreateDefaultPlan(
        projectId: String,
        type: String,
        scannerName: String?
    ): ScanPlan {
        val name = defaultScanPlanName(type, scannerName)

        var defaultScanPlan = scanPlanDao.find(projectId, type, name)
        if (defaultScanPlan != null) {
            // 更新支持扫描的类型与扫描器一致
            val scanner = scannerDao.findByName(defaultScanPlan.scanner)
            require(scanner != null)
            if (!defaultScanPlan.scanTypes.containsAll(scanner.supportScanTypes) ||
                !scanner.supportPackageTypes.containsAll(defaultScanPlan.scanTypes)) {
                defaultScanPlan = defaultScanPlan.copy(
                    scanTypes = scanner.supportScanTypes,
                    lastModifiedDate = LocalDateTime.now(),
                    lastModifiedBy = SecurityUtils.getUserId()
                )
                scanPlanDao.save(defaultScanPlan)
            }
            return ScanPlanConverter.convert(defaultScanPlan)
        }

        return createDefaultScanPlan(projectId, type, scannerName)
    }

    override fun delete(projectId: String, id: String) {
        logger.info("deleteScanPlan userId:${SecurityUtils.getUserId()}, projectId:$projectId, planId:$id")

        val scanPlan = scanPlanDao.findByProjectIdAndId(projectId, id)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId, id)

        if (scanPlan.readOnly) {
            permissionCheckHandler.permissionManager.checkPrincipal(SecurityUtils.getUserId(), PrincipalType.ADMIN)
        }

        // 方案正在使用，不能删除
        checkRunning(id)
        scanPlanDao.delete(projectId, id)
    }

    override fun update(request: UpdateScanPlanRequest): ScanPlan {
        val operator = SecurityUtils.getUserId()
        logger.info("userId:$operator, updateScanPlan:[${request.id}]")
        with(request) {
            if (id.isNullOrEmpty() || projectId.isNullOrEmpty()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
            }

            val scanPlan = ScanPlanConverter.convert(request)
            checkPermission(scanPlan.projectId!!, scanPlan.rule)

            val tScanPlan = scanPlanDao.findByProjectIdAndId(projectId!!, id!!)
                ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId!!, id!!)

            if (tScanPlan.readOnly) {
                permissionCheckHandler.permissionManager.checkPrincipal(SecurityUtils.getUserId(), PrincipalType.ADMIN)
            }

            val savedScanPlan = scanPlanDao.save(
                tScanPlan.copy(
                    name = scanPlan.name ?: tScanPlan.name,
                    description = scanPlan.description ?: tScanPlan.description,
                    scanOnNewArtifact = scanPlan.scanOnNewArtifact ?: tScanPlan.scanOnNewArtifact,
                    repoNames = scanPlan.repoNames ?: tScanPlan.repoNames,
                    rule = scanPlan.rule?.toJsonString() ?: tScanPlan.rule
                )
            )
            return ScanPlanConverter.convert(savedScanPlan)
        }
    }

    override fun scanPlanInfo(request: PlanCountRequest): ScanPlanInfo? {
        with(request) {
            val scanPlan = scanPlanDao.find(projectId, id)
                ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId, id)
            return if (startTime != null && endTime != null) {
                val subScanTasks = planArtifactLatestSubScanTaskDao.findBy(request)
                ScanPlanConverter.convert(scanPlan, subScanTasks)
            } else {
                val scanTask = scanPlan.latestScanTaskId?.let { scanTaskDao.findById(it) }
                val artifactCount = planArtifactLatestSubScanTaskDao.planArtifactCount(scanPlan.id!!)
                ScanPlanConverter.convert(scanPlan, scanTask, artifactCount)
            }
        }
    }

    override fun artifactPlanList(request: ArtifactPlanRelationRequest): ArtifactPlanRelations {
        val relations = artifactPlanRelation(request)
        return if (relations.isEmpty()) {
            ArtifactPlanRelations(null, relations)
        } else {
            val scanStatus = relations.map { it.status }.let(ScanPlanConverter::artifactStatus)
            ArtifactPlanRelations(scanStatus, relations)
        }
    }

    private fun artifactPlanRelation(
        request: ArtifactPlanRelationRequest,
        checkPermission: Boolean = true
    ): List<ArtifactPlanRelation> {
        with(request) {
            ScanParamUtil.checkParam(
                repoType = RepositoryType.valueOf(repoType),
                packageKey = packageKey,
                version = version,
                fullPath = fullPath
            )
            if (fullPath == null) {
                val packageVersion = Request.request {
                    packageClient.findVersionByName(projectId, repoName, packageKey!!, version!!)
                }

                fullPath = packageVersion?.contentPath ?: packageVersion?.manifestPath
                fullPath ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, packageKey!!, version!!)
            }
            if (checkPermission)
                permissionCheckHandler.checkNodePermission(projectId, repoName, fullPath!!, PermissionAction.READ)
            val subtasks = planArtifactLatestSubScanTaskDao.findAll(projectId, repoName, fullPath!!)
            val planIds = subtasks.mapNotNull { it.planId }
            val scanPlanMap = scanPlanDao.findByIds(planIds, true).associateBy { it.id!! }
            return subtasks.map {
                if (it.planId == null) {
                    ScanPlanConverter.convertToArtifactPlanRelation(it)
                } else {
                    ScanPlanConverter.convertToArtifactPlanRelation(it, scanPlanMap[it.planId]!!)
                }
            }
        }
    }

    override fun artifactPlanStatus(request: ArtifactPlanRelationRequest): String? {
        val relations = artifactPlanRelation(request, false)

        if (relations.isEmpty()) {
            return null
        }

        return ScanPlanConverter.artifactStatus(relations.map { it.status })
    }

    private fun checkRunning(planId: String) {
        if (scanTaskDao.existsByPlanIdAndStatus(planId, runningStatus)) {
            throw ErrorCodeException(ScannerMessageCode.SCAN_PLAN_DELETE_FAILED)
        }
    }

    override fun scanLicensePlanInfo(request: PlanCountRequest): ScanLicensePlanInfo? {
        with(request) {
            val scanPlan = scanPlanDao.find(projectId, id)
                ?: throw throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, projectId, id)
            return if (startTime != null && endTime != null) {
                val subScanTasks = planArtifactLatestSubScanTaskDao.findBy(request)
                ScanLicenseConverter.convert(scanPlan, subScanTasks)
            } else {
                val scanTask = scanPlan.latestScanTaskId?.let { scanTaskDao.findById(it) }
                val artifactCount = planArtifactLatestSubScanTaskDao.planArtifactCount(scanPlan.id!!)
                ScanLicenseConverter.convert(scanPlan, scanTask, artifactCount)
            }
        }
    }

    private fun createDefaultScanPlan(projectId: String, type: String, scannerName: String?): ScanPlan {
        // 项目成员即可创建默认扫描方案
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.READ)
        val name = defaultScanPlanName(type, scannerName)
        val scanner = scannerName?.let { scannerService.get(it) } ?: scannerService.default()
        return try {
            val scanPlan = ScanPlan(
                projectId = projectId,
                name = name,
                type = type,
                scanTypes = scanner.supportScanTypes,
                scanner = scanner.name,
                rule = RuleConverter.convert(projectId, emptyList(), type)
            )
            scanPlan.readOnly = true
            create(scanPlan)
        } catch (e: ErrorCodeException) {
            if (e.messageCode == CommonMessageCode.RESOURCE_EXISTED) {
                findByName(projectId, type, name)!!
            } else {
                throw e
            }
        }
    }

    /**
     * [rule]中只能有一个projectId且与[projectId]一致
     */
    private fun checkPermission(projectId: String, rule: Rule?) {
        if (rule != null) {
            val ruleProjectIds = RuleUtil.getProjectIds(rule)
            val ruleProjectId = ruleProjectIds.firstOrNull()
            if (ruleProjectIds.size != 1 || ruleProjectId != projectId) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, rule)
            }
        }
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.MANAGE)
    }

    private fun defaultScanPlanName(type: String, scanner: String? = null) = if (scanner == null) {
        "DEFAULT_$type"
    } else {
        "DEFAULT_${type}_$scanner"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanPlanServiceImpl::class.java)
        private val runningStatus = listOf(
            ScanTaskStatus.PENDING.name,
            ScanTaskStatus.SCANNING_SUBMITTING.name,
            ScanTaskStatus.SCANNING_SUBMITTED.name
        )
        private const val PLAN_NAME_LENGTH_MAX = 32
    }
}
