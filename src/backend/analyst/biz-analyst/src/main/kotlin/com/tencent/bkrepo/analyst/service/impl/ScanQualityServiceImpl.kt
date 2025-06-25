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
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.model.TScanPlan
import com.tencent.bkrepo.analyst.pojo.request.ScanQualityUpdateRequest
import com.tencent.bkrepo.analyst.pojo.response.ScanQuality
import com.tencent.bkrepo.analyst.service.LicenseScanQualityService
import com.tencent.bkrepo.analyst.service.ScanQualityService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.utils.RuleUtil
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanType
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.api.util.readJsonString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ScanQualityServiceImpl(
    private val permissionCheckHandler: ScannerPermissionCheckHandler,
    private val scanPlanDao: ScanPlanDao,
    private val scannerService: ScannerService,
    private val licenseScanQualityService: LicenseScanQualityService,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
) : ScanQualityService {
    override fun getScanQuality(planId: String): ScanQuality {
        val scanPlan = scanPlanDao.get(planId)
        permissionCheckHandler.checkProjectPermission(scanPlan.projectId, PermissionAction.MANAGE)
        return ScanQuality.create(scanPlan.scanQuality)
    }

    override fun updateScanQuality(planId: String, request: ScanQualityUpdateRequest): Boolean {
        val scanPlan = scanPlanDao.get(planId)
        permissionCheckHandler.checkProjectPermission(scanPlan.projectId, PermissionAction.MANAGE)
        val updateResult = scanPlanDao.updateQuality(planId, request.toMap())
        if (updateResult.matchedCount == 0L) {
            return false
        }
        return true
    }

    override fun checkScanQualityRedLine(planId: String, scanResultOverview: Map<String, Number>): Boolean {
        val tScanPlan = scanPlanDao.get(planId)
        val scanner = scannerService.get(tScanPlan.scanner)
        // 获取方案质量规则
        val scanQuality = scanPlanDao.get(planId).scanQuality
        return checkScanQualityRedLine(scanQuality, scanResultOverview, scanner)
    }

    override fun checkScanQualityRedLine(
        scanQuality: Map<String, Any>,
        scanResultOverview: Map<String, Number>,
        scanner: Scanner
    ): Boolean {
        // 检查质量规则是否通过
        CveOverviewKey.values().forEach { overviewKey ->
            if (checkRedLine(overviewKey, scanResultOverview, scanQuality)) {
                return false
            }
        }
        if (scanner.supportScanTypes.contains(ScanType.LICENSE.name)) {
            return licenseScanQualityService.checkLicenseScanQualityRedLine(scanQuality, scanResultOverview)
        }
        return true
    }

    override fun shouldForbid(projectId: String, repoName: String, fullPath: String, sha256: String): Boolean {
        val plans = scanPlanDao.findByProjectIdAndRepoName(projectId, repoName)
        val planSubtasks = planArtifactLatestSubScanTaskDao
            .findAll(projectId, repoName, fullPath)
            .associateBy { it.planId }
        plans.forEach { plan ->
            if (shouldForbid(projectId, repoName, fullPath, sha256, plan, planSubtasks[plan.id])) {
                return true
            }
        }
        return false
    }

    override fun shouldForbidBeforeScanned(projectId: String, repoName: String, fullPath: String): Boolean {
        return scanPlanDao
            .findByProjectIdAndRepoName(projectId, repoName)
            .any { plan ->
                val forbidNotScanned = plan.scanQuality[ScanQuality::forbidNotScanned.name] == true
                forbidNotScanned && RuleUtil.match(plan.rule.readJsonString(), projectId, repoName, fullPath)
            }
    }

    /**
     * 判断[overviewKey]对应的CVE数量是否超过[quality]中指定的红线
     *
     * @return true 超过， false 未超过
     */
    private fun checkRedLine(
        overviewKey: CveOverviewKey,
        overview: Map<String, Number>,
        quality: Map<String, Any>
    ): Boolean {
        val cveCount = overview[overviewKey.key]?.toLong() ?: 0L
        val redLine = quality[overviewKey.level.levelName] as Long? ?: Long.MAX_VALUE
        return cveCount > redLine
    }

    private fun shouldForbid(
        projectId: String,
        repoName: String,
        fullPath: String,
        sha256: String,
        plan: TScanPlan,
        planSubtask: TPlanArtifactLatestSubScanTask?
    ): Boolean {
        // 未扫描过且开启了禁用未扫描制品
        val scanned = planSubtask?.sha256 == sha256 &&
                planSubtask.projectId == projectId &&
                planSubtask.repoName == repoName &&
                planSubtask.fullPath == fullPath
        if (!scanned && plan.scanQuality[ScanQuality::forbidNotScanned.name] == true) {
            logger.info("forbid [$projectId/$repoName$fullPath][$sha256], reason: forbid not scanned")
            return true
        }

        // 未扫描过时不禁用
        if (!scanned) {
            return false
        }

        // 扫描过时判断是否通过质量规则
        val scanner = scannerService.get(plan.scanner)
        val scanResultOverview = planSubtask?.scanResultOverview ?: emptyMap()
        val shouldForbid = checkScanQualityRedLine(plan.scanQuality, scanResultOverview, scanner)
        if (shouldForbid) {
            logger.info("forbid [$projectId/$repoName$fullPath][$sha256], reason: exceed red line")
        }
        return shouldForbid
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanQualityServiceImpl::class.java)
    }
}
