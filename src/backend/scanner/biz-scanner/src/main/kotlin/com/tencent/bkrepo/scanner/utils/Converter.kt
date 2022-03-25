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

@file:Suppress("DEPRECATION")

package com.tencent.bkrepo.scanner.utils

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Rule.NestedRule
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.BinAuditorScanner
import com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor.CveSecItem
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.LEVEL_CRITICAL
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.LEVEL_HIGH
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.LEVEL_LOW
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.LEVEL_MID
import com.tencent.bkrepo.common.scanner.pojo.scanner.utils.normalizedLevel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.scanner.model.SubScanTaskDefinition
import com.tencent.bkrepo.scanner.model.TScanPlan
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.model.TSubScanTask
import com.tencent.bkrepo.scanner.pojo.PlanType
import com.tencent.bkrepo.scanner.pojo.ScanPlan
import com.tencent.bkrepo.scanner.pojo.ScanStatus
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.ScanTriggerType
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.pojo.request.BatchScanRequest
import com.tencent.bkrepo.scanner.pojo.request.CreateScanPlanRequest
import com.tencent.bkrepo.scanner.pojo.request.PlanArtifactRequest
import com.tencent.bkrepo.scanner.pojo.request.ScanRequest
import com.tencent.bkrepo.scanner.pojo.request.SingleScanRequest
import com.tencent.bkrepo.scanner.pojo.request.UpdateScanPlanRequest
import com.tencent.bkrepo.scanner.pojo.response.ArtifactPlanRelation
import com.tencent.bkrepo.scanner.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.scanner.pojo.response.PlanArtifactInfo
import com.tencent.bkrepo.scanner.pojo.response.ScanPlanBase
import com.tencent.bkrepo.scanner.pojo.response.ScanPlanInfo
import com.tencent.bkrepo.scanner.pojo.rule.ArtifactRule
import com.tencent.bkrepo.scanner.pojo.rule.RuleArtifact
import com.tencent.bkrepo.scanner.pojo.rule.RuleType
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.time.format.DateTimeFormatter

object Converter {
    fun convert(subScanTask: TSubScanTask, scanner: Scanner): SubScanTask = with(subScanTask) {
        SubScanTask(
            taskId = id!!,
            parentScanTaskId = parentScanTaskId,
            scanner = scanner,
            sha256 = sha256,
            size = size,
            credentialsKey = credentialsKey
        )
    }

    fun convert(scanTask: TScanTask, scanPlan: TScanPlan? = null): ScanTask = with(scanTask) {
        ScanTask(
            taskId = id!!,
            createdBy = createdBy,
            triggerDateTime = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
            startDateTime = startDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            finishedDateTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            status = status,
            scanPlan = scanPlan?.let { convert(it) },
            rule = scanTask.rule?.readJsonString(),
            total = total,
            scanning = scanning,
            failed = failed,
            scanned = scanned,
            scanner = scanner,
            scannerType = scannerType,
            scannerVersion = scannerVersion,
            scanResultOverview = scanResultOverview
        )
    }

    fun convert(scanPlan: TScanPlan): ScanPlan {
        return with(scanPlan) {
            ScanPlan(
                projectId = projectId,
                name = name,
                type = type,
                scanner = scanner,
                description = description,
                scanOnNewArtifact = scanOnNewArtifact,
                repoNames = repoNames,
                rule = rule.readJsonString(),
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    fun convert(scanPlan: ScanPlan): ScanPlanBase {
        return with(scanPlan) {
            ScanPlanBase(
                id = id!!,
                name = name,
                type = type!!,
                scanner = scanner!!,
                description = description!!,
                projectId = projectId!!,
                autoScan = scanOnNewArtifact!!,
                scanOnNewArtifact = scanOnNewArtifact!!,
                repoNameList = repoNames!!,
                repoNames = repoNames!!,
                artifactRules = rule?.let { convert(it) } ?: emptyList(),
                rule = rule,
                createdBy = createdBy!!,
                createdDate = createdDate!!,
                lastModifiedBy = lastModifiedBy!!,
                lastModifiedDate = lastModifiedDate!!
            )
        }
    }

    fun convert(scanPlanRequest: UpdateScanPlanRequest, curRepoNames: List<String>, curRule: Rule): ScanPlan {
        return with(scanPlanRequest) {
            val rule = if (repoNameList?.isEmpty() == true && artifactRules?.isEmpty() == true) {
                null
            } else {
                convert(projectId!!, repoNameList ?: curRepoNames, artifactRules ?: convert(curRule))
            }
            ScanPlan(
                id = id,
                projectId = projectId,
                name = name,
                description = description,
                scanOnNewArtifact = autoScan,
                repoNames = repoNameList,
                rule = rule
            )
        }
    }

    fun convert(scanPlanRequest: CreateScanPlanRequest): ScanPlan {
        return with(scanPlanRequest) {
            ScanPlan(
                projectId = projectId,
                name = name,
                type = type,
                scanner = scanner,
                description = description,
                scanOnNewArtifact = autoScan,
                repoNames = repoNameList,
                rule = convert(projectId, repoNameList, artifactRules)
            )
        }
    }

    fun convert(scanPlan: TScanPlan, latestScanTask: TScanTask?): ScanPlanInfo {
        with(scanPlan) {
            val critical = latestScanTask?.let { getCveCount(LEVEL_CRITICAL, latestScanTask) } ?: 0L
            val high = latestScanTask?.let { getCveCount(LEVEL_HIGH, latestScanTask) } ?: 0L
            val medium = latestScanTask?.let { getCveCount(LEVEL_MID, latestScanTask) } ?: 0L
            val low = latestScanTask?.let { getCveCount(LEVEL_LOW, latestScanTask) } ?: 0L
            val artifactCount = latestScanTask?.total ?: 0L
            val status = latestScanTask?.let { convertToScanStatus(it.status).name } ?: ScanStatus.INIT.name

            return ScanPlanInfo(
                id = id!!,
                name = name,
                planType = type,
                projectId = projectId,
                status = status,
                artifactCount = artifactCount,
                critical = critical,
                high = high,
                medium = medium,
                low = low,
                total = critical + high + medium + low,
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastScanDate = latestScanTask?.startDateTime?.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    fun convert(request: PlanArtifactRequest): PlanArtifactRequest {
        request.highestLeakLevel = request.highestLeakLevel?.let { normalizedLevel(it) }
        request.subScanTaskStatus = request.status
            ?.let { convertToSubScanTaskStatus(ScanStatus.valueOf(it)) }
            ?.map { it.name }
        return request
    }

    fun convertToPlanArtifactInfo(subScanTask: SubScanTaskDefinition, createdBy: String): PlanArtifactInfo {
        return with(subScanTask) {
            val duration = if (startDateTime != null && finishedDateTime != null) {
                Duration.between(startDateTime, finishedDateTime).toMillis()
            } else {
                0L
            }
            PlanArtifactInfo(
                recordId = id!!,
                subTaskId = id,
                name = artifactName,
                packageKey = packageKey,
                version = version,
                fullPath = fullPath,
                repoType = repoType,
                repoName = repoName,
                highestLeakLevel = scanResultOverview?.let { highestLeakLevel(it) },
                duration = duration,
                finishTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                status = convertToScanStatus(status).name,
                createdBy = createdBy,
                createdDate = createdDate.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    fun convertToArtifactPlanRelation(subScanTask: SubScanTaskDefinition): ArtifactPlanRelation {
        val planType = if (subScanTask.repoType == RepositoryType.GENERIC.name) {
            PlanType.MOBILE.name
        } else {
            PlanType.DEPENDENT.name
        }
        return with(subScanTask) {
            ArtifactPlanRelation(
                id = planId!!,
                planId = planId,
                projectId = projectId,
                planType = planType,
                name = artifactName,
                status = convertToScanStatus(status).name,
                recordId = id!!,
                subTaskId = id
            )
        }
    }

    fun artifactStatus(status: List<String>): String {
        require(status.isNotEmpty())
        var maxStatus: ScanStatus? = null
        status.forEach { curStatus ->
            if (curStatus == ScanStatus.RUNNING.name) {
                return curStatus
            }
            maxStatus = maxStatus
                ?.let { max -> maxOf(ScanStatus.valueOf(curStatus), max) }
                ?: ScanStatus.valueOf(curStatus)
        }
        return maxStatus!!.name
    }

    @Suppress("UNCHECKED_CAST")
    fun convert(
        detailReport: Any?,
        scannerType: String,
        reportType: String,
        pageLimit: PageLimit
    ): Page<ArtifactVulnerabilityInfo> {
        val pageRequest = PageRequest.of(pageLimit.pageNumber, pageLimit.pageSize)
        if (scannerType == BinAuditorScanner.TYPE && reportType == CveSecItem.TYPE && detailReport != null) {
            detailReport as Page<CveSecItem>
            val reports = detailReport.records.map {
                // TODO 添加漏洞详情
                ArtifactVulnerabilityInfo(
                    cveId = it.cveId,
                    severity = it.level!!,
                    pkgName = it.component,
                    installedVersion = it.version,
                    title = it.name,
                    vulnerabilityName = it.name
                )
            }
            return Pages.ofResponse(pageRequest, detailReport.totalRecords, reports)
        }
        return Pages.ofResponse(pageRequest, 0L, emptyList())
    }

    fun convert(request: BatchScanRequest): ScanRequest {
        with(request) {
            val rule = if (repoNames.isEmpty() && artifactRules.isEmpty()) {
                null
            } else {
                convert(projectId, repoNames, artifactRules)
            }
            return ScanRequest(null, request.planId, rule)
        }
    }

    fun convert(triggerType: String): ScanTriggerType {
        return when (triggerType) {
            "MANUAL" -> ScanTriggerType.MANUAL
            "AUTOM" -> ScanTriggerType.ON_NEW_ARTIFACT
            else -> throw SystemErrorException()
        }
    }

    fun convert(request: SingleScanRequest): ScanRequest {
        with(request) {
            require(fullPath != null || packageKey != null && version != null)

            // 创建rule
            val rule = createProjectIdAdnRepoRule(projectId, listOf(repoName))
            if (fullPath != null) {
                rule.rules.add(Rule.QueryRule(NodeDetail::fullPath.name, fullPath!!, OperationType.EQ))
            } else {
                rule.rules.add(Rule.QueryRule(PackageSummary::key.name, packageKey!!, OperationType.EQ))
                rule.rules.add(Rule.QueryRule(RuleArtifact::version.name, version!!, OperationType.EQ))
            }

            return ScanRequest(planId = planId, rule = rule)
        }
    }

    private fun highestLeakLevel(overview: Map<String, Number>): String {
        return if (overview.keys.contains(LEVEL_CRITICAL)) {
            LEVEL_CRITICAL
        } else if (overview.keys.contains(LEVEL_HIGH)) {
            LEVEL_HIGH
        } else if (overview.keys.contains(LEVEL_MID)) {
            LEVEL_MID
        } else {
            LEVEL_LOW
        }
    }

    private fun convertToSubScanTaskStatus(status: ScanStatus): List<SubScanTaskStatus> {
        return when (status) {
            ScanStatus.INIT -> listOf(SubScanTaskStatus.CREATED, SubScanTaskStatus.PULLED, SubScanTaskStatus.ENQUEUED)
            ScanStatus.RUNNING -> listOf(SubScanTaskStatus.EXECUTING)
            ScanStatus.STOP -> listOf(SubScanTaskStatus.STOP)
            ScanStatus.FAILED -> listOf(SubScanTaskStatus.FAILED)
            ScanStatus.SUCCESS -> listOf(SubScanTaskStatus.SUCCESS)
        }
    }

    private fun convertToScanStatus(status: String?): ScanStatus {
        return when (status) {
            SubScanTaskStatus.CREATED.name,
            SubScanTaskStatus.PULLED.name,
            SubScanTaskStatus.ENQUEUED.name,
            ScanTaskStatus.PENDING.name -> ScanStatus.INIT

            SubScanTaskStatus.EXECUTING.name,
            ScanTaskStatus.SCANNING_SUBMITTING.name,
            ScanTaskStatus.SCANNING_SUBMITTED.name -> ScanStatus.RUNNING

            SubScanTaskStatus.STOP.name,
            ScanTaskStatus.PAUSE.name,
            ScanTaskStatus.STOPPED.name -> ScanStatus.STOP

            SubScanTaskStatus.SUCCESS.name,
            ScanTaskStatus.FINISHED.name -> ScanStatus.SUCCESS

            SubScanTaskStatus.TIMEOUT.name,
            SubScanTaskStatus.FAILED.name -> ScanStatus.FAILED
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, status.toString())
        }
    }

    private fun getCveCount(level: String, scanTask: TScanTask): Long {
        if (scanTask.scannerType == BinAuditorScanner.TYPE) {
            val key = BinAuditorScanExecutorResult.overviewKeyOfCve(level)
            return scanTask.scanResultOverview?.get(key) ?: 0L
        }

        return 0L
    }

    private fun convert(projectId: String, repoNames: List<String>, rules: List<ArtifactRule>): Rule {
        val rule = createProjectIdAdnRepoRule(projectId, repoNames)

        if (rules.isEmpty()) {
            return rule
        } else if (rules.size == 1) {
            val nameAndVersionRule = rule(rules[0])
            if (nameAndVersionRule is NestedRule) {
                rule.rules.addAll(nameAndVersionRule.rules)
            } else {
                rule.rules.add(nameAndVersionRule)
            }
        } else {
            rules
                .asSequence()
                .filter { it.versionRule != null || it.nameRule != null }
                .map { artifactRule -> rule(artifactRule) }
                .let { rule.rules.add(NestedRule(it.toMutableList(), NestedRule.RelationType.OR)) }
        }

        return rule
    }

    private fun rule(artifactRule: ArtifactRule): Rule {
        val nameRule = artifactRule.nameRule?.let { convertRule(RuleArtifact::name.name, it) }
        val versionRule = artifactRule.versionRule?.let { convertRule(RuleArtifact::version.name, it) }
        return rule(nameRule, versionRule)
    }

    private fun convertRule(field: String, rule: com.tencent.bkrepo.scanner.pojo.rule.Rule): Rule.QueryRule {
        return Rule.QueryRule(field, rule.value, convertRuleType(rule.type))
    }

    private fun convertRuleType(type: RuleType): OperationType {
        return when (type) {
            RuleType.EQ -> OperationType.EQ
            RuleType.IN -> OperationType.IN
            RuleType.REGEX -> OperationType.MATCH
        }
    }

    /**
     * 添加projectId和repoName规则
     */
    private fun createProjectIdAdnRepoRule(projectId: String, repoNames: List<String>): NestedRule {
        val rules = mutableListOf<Rule>(
            Rule.QueryRule(NodeDetail::projectId.name, projectId, OperationType.EQ)
        )
        if (repoNames.isNotEmpty()) {
            rules.add(Rule.QueryRule(NodeDetail::repoName.name, repoNames, OperationType.IN))
        }

        return NestedRule(rules, NestedRule.RelationType.AND)
    }

    private fun rule(nameRule: Rule?, versionRule: Rule?): Rule {
        require(nameRule != null || versionRule != null)
        if (nameRule == null) {
            return versionRule!!
        }

        if (versionRule == null) {
            return nameRule
        }

        return NestedRule(mutableListOf(nameRule, versionRule), NestedRule.RelationType.AND)
    }

    private fun convert(rule: Rule): List<ArtifactRule> {
        require(rule is NestedRule)

        rule.rules.forEach { innerRule ->
            val isArtifactRule = isArtifactRule(innerRule)

            if (isArtifactRule && innerRule is Rule.QueryRule) {
                return listOf(artifactRule(innerRule))
            }

            if (isArtifactRule && innerRule is NestedRule && innerRule.relation == NestedRule.RelationType.AND) {
                return listOf(artifactRule(innerRule))
            }

            if (isArtifactRule && innerRule is NestedRule && innerRule.relation == NestedRule.RelationType.OR) {
                return innerRule.rules.map { artifactRule(it) }
            }
        }

        return emptyList()
    }

    private fun isArtifactRule(rule: Rule): Boolean {
        with(rule) {
            // 只存在一个artifactRule，version和name只存在一种
            if (this is Rule.QueryRule) {
                return field == RuleArtifact::name.name || field == RuleArtifact::version.name
            }

            // 只存在一个artifactRule，version和name两种rule都存在
            if (this is NestedRule && this.relation == NestedRule.RelationType.AND) {
                val artifactRule = artifactRule(this)
                return artifactRule.nameRule != null && artifactRule.versionRule != null
            }

            // 存在多个artifactRule的情况
            if (this is NestedRule && this.relation == NestedRule.RelationType.OR) {
                val artifactRule = artifactRule(this.rules.first())
                return artifactRule.nameRule != null || artifactRule.versionRule != null
            }

            return false
        }
    }

    private fun artifactRule(rule: Rule): ArtifactRule {
        require(rule is Rule.QueryRule || rule is NestedRule && rule.relation == NestedRule.RelationType.AND)

        return ArtifactRule(
            findRuleFrom(rule, RuleArtifact::name.name),
            findRuleFrom(rule, RuleArtifact::version.name)
        )
    }

    private fun findRuleFrom(rule: Rule, filed: String): com.tencent.bkrepo.scanner.pojo.rule.Rule? {
        require(rule is Rule.QueryRule || rule is NestedRule && rule.relation == NestedRule.RelationType.AND)

        return if (rule is Rule.QueryRule && rule.field == filed) {
            convertRule(rule)
        } else if (rule is NestedRule && rule.relation == NestedRule.RelationType.AND) {
            rule.rules
                .firstOrNull { it is Rule.QueryRule && it.field == filed }
                ?.let { convertRule(it) }
        } else {
            null
        }
    }

    private fun convertRule(rule: Rule): com.tencent.bkrepo.scanner.pojo.rule.Rule {
        require(rule is Rule.QueryRule)

        return com.tencent.bkrepo.scanner.pojo.rule.Rule(
            convertRuleOperationType(rule.operation),
            rule.value.toString()
        )
    }

    private fun convertRuleOperationType(type: OperationType): RuleType {
        return when (type) {
            OperationType.EQ -> RuleType.EQ
            OperationType.MATCH -> RuleType.REGEX
            OperationType.IN -> RuleType.IN
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, type)
        }
    }
}
