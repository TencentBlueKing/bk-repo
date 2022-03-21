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

package com.tencent.bkrepo.scanner.utils

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Rule.NestedRule
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.model.TSubScanTask
import com.tencent.bkrepo.scanner.pojo.ScanPlan
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.pojo.request.CreateScanPlanRequest
import com.tencent.bkrepo.scanner.pojo.request.UpdateScanPlanRequest
import com.tencent.bkrepo.scanner.pojo.rule.ArtifactRule
import com.tencent.bkrepo.scanner.pojo.rule.RuleArtifact
import com.tencent.bkrepo.scanner.pojo.rule.RuleType
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

    fun convert(scanTask: TScanTask): ScanTask = with(scanTask) {
        ScanTask(
            taskId = id!!,
            createdBy = createdBy,
            triggerDateTime = createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
            startDateTime = startDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            finishedDateTime = finishedDateTime?.format(DateTimeFormatter.ISO_DATE_TIME),
            status = status,
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

    fun convert(scanPlanRequest: UpdateScanPlanRequest): ScanPlan {
        return with(scanPlanRequest) {
            ScanPlan(
                id = id,
                projectId = projectId,
                name = name,
                description = description,
                scanOnNewArtifact = autoScan,
                repoNames = repoNameList,
                rule = artifactRules?.let { convert(it) }
            )
        }
    }

    fun convert(scanPlanRequest: CreateScanPlanRequest): ScanPlan {
        return with(scanPlanRequest) {
            ScanPlan(
                projectId = projectId,
                name = name,
                type = type,
                description = description,
                scanOnNewArtifact = autoScan,
                repoNames = repoNameList,
                rule = convert(artifactRules)
            )
        }
    }

    private fun convert(rules: List<ArtifactRule>): Rule {
        return rules
            .asSequence()
            .filter { it.versionRule != null || it.nameRule != null }
            .map { rule ->
                val nameRule = rule.nameRule?.let { convertRule(RuleArtifact::name.name, it) }
                val versionRule = rule.versionRule?.let { convertRule(RuleArtifact::version.name, it) }
                rule(nameRule, versionRule)
            }
            .run { NestedRule(this.toMutableList(), NestedRule.RelationType.OR) }
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
}
