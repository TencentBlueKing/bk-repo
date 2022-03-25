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
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.scanner.model.TScanPlan
import com.tencent.bkrepo.scanner.pojo.request.MatchPlanSingleScanRequest
import com.tencent.bkrepo.scanner.pojo.rule.RuleArtifact
import java.io.File

object RuleMatcher {
    fun match(request: MatchPlanSingleScanRequest, plan: TScanPlan): Boolean {
        return with(request) {
            if (fullPath != null) {
                match(fullPath!!, plan.rule.readJsonString())
            } else {
                match(packageName!!, version!!, plan.rule.readJsonString())
            }
        }
    }

    private fun match(packageName: String, packageVersion: String, rule: Rule): Boolean {
        val packageNameVersionMap = packageNameToVersions(rule)
        return if (packageNameVersionMap.isEmpty()) {
            true
        } else {
            packageNameVersionMap[packageName]?.let { packageVersion in it } ?: false
        }
    }

    private fun match(fullPath: String, rule: Rule): Boolean {
        val name = File(fullPath).name
        val packageNameVersionMap = packageNameToVersions(rule)

        // 未配置任何规则或仅配置了artifactName规则，未配置版本规则
        return packageNameVersionMap.isEmpty() || packageNameVersionMap[name]?.isEmpty() == true
    }

    /**
     * 从[rule]中解析出packageName对应的要查询的所有版本
     */
    fun packageNameToVersions(rule: Rule): Map<String?, MutableList<String>> {
        if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.AND) {
            val map = HashMap<String?, MutableList<String>>()
            // 获取nameRule
            val nameRule = rule.rules.firstOrNull {
                it is Rule.QueryRule && it.field == RuleArtifact::name.name
            } as Rule.QueryRule?

            // 获取versionRule
            val versionRule = rule.rules.firstOrNull {
                it is Rule.QueryRule && it.field == RuleArtifact::version.name
            } as Rule.QueryRule?

            // nameRule或versionRule存在的时候不包含其他rule
            if (nameRule != null && versionRule != null) {
                map.getOrPut(nameRule.value.toString()) { ArrayList() }.add(versionRule.value.toString())
            } else if (nameRule != null) {
                map.getOrPut(nameRule.value.toString()) { ArrayList() }
            } else if (versionRule != null) {
                map.getOrPut(null) { ArrayList() }.add(versionRule.value.toString())
            }
            else {
                rule.rules.map { packageNameToVersions(it) }.forEach { map.putAll(it) }
            }
            return map
        }

        if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.OR) {
            val map = HashMap<String?, MutableList<String>>()
            rule.rules.forEach { map.putAll(packageNameToVersions(it)) }
            return map
        }

        return emptyMap()
    }
}
