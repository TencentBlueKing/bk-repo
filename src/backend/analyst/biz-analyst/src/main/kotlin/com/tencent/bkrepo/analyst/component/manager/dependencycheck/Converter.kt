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

package com.tencent.bkrepo.analyst.component.manager.dependencycheck

import com.tencent.bkrepo.common.checker.pojo.Cvssv2
import com.tencent.bkrepo.common.checker.pojo.Cvssv3
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.CvssV2
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.CvssV3
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.result.DependencyItem
import com.tencent.bkrepo.common.analysis.pojo.scanner.dependencycheck.scanner.DependencyScanner
import com.tencent.bkrepo.common.api.constant.SYSTEM_USER
import com.tencent.bkrepo.analyst.component.manager.dependencycheck.model.TDependencyItem
import com.tencent.bkrepo.analyst.component.manager.dependencycheck.model.TDependencyItemData
import com.tencent.bkrepo.analyst.component.manager.knowledgebase.TCve
import java.time.LocalDateTime
import java.util.Locale

object Converter {
    /**
     * 未计算cvss评分
     */
    private const val NO_CVSS_SCORE = 0.0

    private const val UNKNOWN = ""

    fun convertToCve(dependencyItem: DependencyItem): TCve {
        return with(dependencyItem) {
            val now = LocalDateTime.now()
            TCve(
                createdBy = SYSTEM_USER,
                createdDate = now,
                lastModifiedBy = SYSTEM_USER,
                lastModifiedDate = now,
                component = dependency,
                versionEffected = version,
                name = name,
                description = description,
                officialSolution = officialSolution,
                defenseSolution = defenseSolution,
                references = references ?: emptyList(),
                pocId = pocIdOf(cveId),
                cveId = cveId,
                cvssRank = severity,
                cvss = NO_CVSS_SCORE,
                cvssV3 = cvssV3?.let { convert(it) },
                cvssV2 = cvssV2Vector?.let { convert(it) }
            )
        }
    }

    fun convert(dependencyItem: DependencyItem): TDependencyItemData {
        return with(dependencyItem) {
            TDependencyItemData(
                path = path ?: "",
                component = dependency,
                versions = setOf(version),
                cveId = cveId,
                severity = severity
            )
        }
    }

    fun convert(dependencyItem: TDependencyItem, cve: TCve): DependencyItem {
        return with(cve) {
            DependencyItem(
                cveId = dependencyItem.data.cveId,
                name = name,
                dependency = component,
                version = dependencyItem.data.versions.firstOrNull() ?: "",
                severity = dependencyItem.data.severity,
                description = description,
                officialSolution = officialSolution,
                defenseSolution = defenseSolution,
                references = references,
                cvssV2Vector = cvssV2?.let { convert(it) },
                cvssV3 = cvssV3?.let { convert(it) },
                path = dependencyItem.data.path
            )
        }
    }

    private fun convert(cvssV2: Cvssv2): CvssV2 {
        return with(cvssV2) {
            CvssV2(
                baseScore = score,
                confidentialityImpact = confidentialImpact,
                integrityImpact = integrityImpact,
                availabilityImpact = availabilityImpact,
                accessVector = accessVector,
                attackComplexity = accessComplexity,
                authentication = authenticationr
            )
        }
    }

    private fun convert(cvssV3: Cvssv3): CvssV3 {
        return with(cvssV3) {
            CvssV3(
                baseScore = baseScore,
                confidentialityImpact = confidentialityImpact,
                integrityImpact = integrityImpact,
                availabilityImpact = availabilityImpact,
                attackVector = attackVector,
                attackComplexity = attackComplexity,
                privilegesRequired = privilegesRequired,
                userInteraction = userInteraction,
                scope = scope
            )
        }
    }

    private fun convert(cvssV2: CvssV2): Cvssv2 {
        return with(cvssV2) {
            Cvssv2(
                accessComplexity = attackComplexity,
                accessVector = accessVector,
                authenticationr = authentication,
                availabilityImpact = availabilityImpact,
                confidentialImpact = confidentialityImpact,
                exploitabilityScore = UNKNOWN,
                impactScore = UNKNOWN,
                integrityImpact = integrityImpact,
                score = baseScore,
                severity = UNKNOWN,
                version = UNKNOWN
            )
        }
    }

    private fun convert(cvssV3: CvssV3): Cvssv3 {
        return with(cvssV3) {
            Cvssv3(
                attackComplexity = attackComplexity,
                attackVector = attackVector,
                availabilityImpact = availabilityImpact,
                baseScore = baseScore,
                baseSeverity = UNKNOWN,
                confidentialityImpact = confidentialityImpact,
                exploitabilityScore = UNKNOWN,
                impactScore = UNKNOWN,
                integrityImpact = integrityImpact,
                privilegesRequired = privilegesRequired,
                scope = scope,
                userInteraction = userInteraction,
                version = UNKNOWN
            )
        }
    }

    fun pocIdOf(cveId: String) = "${DependencyScanner.TYPE.lowercase(Locale.getDefault())}-$cveId"
}
