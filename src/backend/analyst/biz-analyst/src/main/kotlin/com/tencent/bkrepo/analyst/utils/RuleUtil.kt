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

package com.tencent.bkrepo.analyst.utils

import com.tencent.bkrepo.analyst.pojo.rule.RuleArtifact
import com.tencent.bkrepo.analyst.pojo.rule.RuleArtifact.Companion.RULE_FIELD_LATEST_VERSION
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.matcher.RuleMatcher
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary

object RuleUtil {
    fun getProjectIds(rule: Rule?): List<String> {
        return fieldValueFromRule(rule, NodeInfo::projectId.name)
    }

    fun getRepoNames(rule: Rule?): List<String> {
        return fieldValueFromRule(rule, NodeInfo::repoName.name)
    }

    /**
     * 在nestedRule第一层找需要字段的值
     * 如果指定要扫描的projectId或repoName，必须relation为AND，在nestedRule里面的第一层rule包含对应的匹配条件
     */
    @Suppress("UNCHECKED_CAST")
    fun fieldValueFromRule(rule: Rule?, field: String): List<String> {
        return when (rule) {
            is Rule.QueryRule -> fieldValue(rule, field)
            is Rule.FixedRule -> fieldValue(rule.wrapperRule, field)
            is Rule.NestedRule -> valuesFromNestedRule(rule, field)
            else -> emptyList()
        }
    }

    private fun fieldValue(rule: Rule.QueryRule, field: String): List<String> {
        return if (rule.field == field) {
            listOf(rule.value.toString())
        } else {
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun valuesFromNestedRule(rule: Rule.NestedRule, field: String): List<String> {
        if (rule.relation != Rule.NestedRule.RelationType.AND) {
            return emptyList()
        }

        val fieldValues = ArrayList<String>()
        rule.rules
            .asSequence()
            .filterIsInstance(Rule.QueryRule::class.java)
            .filter { it.field == field }
            .forEach {
                if (it.operation == OperationType.EQ) {
                    fieldValues.add(it.value as String)
                } else if (it.operation == OperationType.IN) {
                    fieldValues.addAll(it.value as Collection<String>)
                }
            }
        return fieldValues
    }

    fun match(rule: Rule, projectId: String, repoName: String, fullPath: String): Boolean {
        val valuesToMatch = mapOf(
            NodeDetail::projectId.name to projectId,
            NodeDetail::repoName.name to repoName,
            RuleArtifact::name.name to fullPath.substringAfterLast(CharPool.SLASH)
        )
        return RuleMatcher.match(rule, valuesToMatch)
    }

    fun match(
        rule: Rule,
        projectId: String,
        repoName: String,
        packageType: String,
        packageName: String,
        packageVersion: String,
    ): Boolean {
        val valuesToMatch = mapOf<String, Any>(
            PackageSummary::projectId.name to projectId,
            PackageSummary::repoName.name to repoName,
            PackageSummary::type.name to packageType,
            RuleArtifact::name.name to packageName,
            RuleArtifact::version.name to packageVersion,
            // 默认当前正在创建的是最新版本
            RULE_FIELD_LATEST_VERSION to true
        )
        return RuleMatcher.match(rule, valuesToMatch)
    }
}
