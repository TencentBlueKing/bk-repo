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

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Rule.NestedRule
import com.tencent.bkrepo.common.query.model.Rule.NestedRule.RelationType.AND
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.analyst.pojo.rule.RuleArtifact

object RuleConverter {

    fun convert(sourceRule: Rule?, planType: String?, projectIds: List<String>): Rule {
        // 兼容许可证扫描的planType:MAVEN_LICENSE
        val targetRule = createProjectIdAndRepoRule(projectIds, emptyList(), planType)
        // 将sourceRule中除projectId相关外的rule都合并到targetRule中
        sourceRule?.let { mergeInto(it, targetRule, listOf(NodeInfo::projectId.name)) }
        return targetRule
    }

    fun convert(projectId: String, repoNames: List<String>, repoType: String? = null): Rule {
        return createProjectIdAndRepoRule(listOf(projectId), repoNames, repoType)
    }

    fun convert(projectId: String, repoName: String, fullPath: String): Rule {
        val rule = createProjectIdAndRepoRule(listOf(projectId), listOf(repoName))
        rule.rules.add(Rule.QueryRule(NodeDetail::fullPath.name, fullPath, OperationType.EQ))
        return rule
    }

    fun convert(projectId: String, repoName: String, packageKey: String, version: String): Rule {
        val rule = createProjectIdAndRepoRule(listOf(projectId), listOf(repoName))
        rule.rules.add(Rule.QueryRule(PackageSummary::key.name, packageKey, OperationType.EQ))
        rule.rules.add(Rule.QueryRule(RuleArtifact::version.name, version, OperationType.EQ))
        return rule
    }

    /**
     * 添加projectId和repoName规则
     */
    private fun createProjectIdAndRepoRule(
        projectIds: List<String>,
        repoNames: List<String>,
        repoType: String? = null
    ): NestedRule {
        val rules = if (projectIds.size == 1) {
            mutableListOf<Rule>(Rule.QueryRule(NodeDetail::projectId.name, projectIds.first(), OperationType.EQ))
        } else {
            mutableListOf<Rule>(Rule.QueryRule(NodeDetail::projectId.name, projectIds, OperationType.IN))
        }

        if (repoType != null && repoType != RepositoryType.GENERIC.name) {
            rules.add(Rule.QueryRule(PackageSummary::type.name, repoType, OperationType.EQ))
        }

        if (repoNames.isNotEmpty()) {
            rules.add(Rule.QueryRule(NodeDetail::repoName.name, repoNames, OperationType.IN))
        }

        return NestedRule(rules, AND)
    }

    /**
     * 将[sourceRule]合并到[targetRule]中，存在于[ignoreFields]中的字段不参最外层规则的合并
     */
    private fun mergeInto(sourceRule: Rule, targetRule: NestedRule, ignoreFields: List<String>) {
        if (sourceRule is Rule.QueryRule && sourceRule.field !in ignoreFields) {
            targetRule.rules.add(sourceRule)
        }

        if (sourceRule is NestedRule) {
            require(sourceRule.relation == targetRule.relation)
            targetRule.rules.addAll(filter(sourceRule, ignoreFields).rules)
        }
    }

    /**
     * 从[rule]中将[fields]相关的query rule移除
     */
    private fun filter(rule: NestedRule, fields: List<String>): NestedRule {
        val filteredRules = ArrayList<Rule>(rule.rules.size)
        rule.rules.forEach {
            if (it is Rule.QueryRule && it.field !in fields) {
                filteredRules.add(it)
            } else if (it is NestedRule) {
                filteredRules.add(filter(it, fields))
            }
        }
        return rule.copy(rules = filteredRules)
    }
}
