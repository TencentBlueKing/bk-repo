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

package com.tencent.bkrepo.analyst.statemachine.iterator

import com.tencent.bkrepo.analyst.pojo.Node
import com.tencent.bkrepo.analyst.pojo.ScanPlan
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import com.tencent.bkrepo.common.metadata.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.analyst.pojo.rule.RuleArtifact
import com.tencent.bkrepo.analyst.utils.Request
import com.tencent.bkrepo.analyst.utils.RuleUtil
import org.springframework.stereotype.Component

/**
 * 迭代器管理器
 */
@Component
class IteratorManager(
    private val nodeClient: NodeClient,
    private val repositoryClient: RepositoryClient,
    private val packageClient: PackageClient
) {
    /**
     * 创建待扫描文件迭代器
     *
     * @param scanTask 扫描任务
     * @param scanner 使用的扫描器
     * @param resume 是否从之前的扫描进度恢复
     */
    fun createNodeIterator(scanTask: ScanTask, scanner: Scanner, resume: Boolean = false): Iterator<Node> {
        val rule = if (scanTask.scanPlan != null && scanTask.rule is Rule.NestedRule) {
            // 存在扫描方案时才修改制品遍历规则
            modifyRule(scanTask.scanPlan!!, scanner, scanTask.rule as Rule.NestedRule)
        } else {
            scanTask.rule
        }

        // TODO projectClient添加分页获取project接口后这边再取消rule需要projectId条件的限制
        require(rule is Rule.NestedRule)
        val projectIds = RuleUtil.getProjectIds(rule)
        val projectIdIterator = projectIds.iterator()

        val isPackageScanPlanType = scanTask.scanPlan != null && scanTask.scanPlan!!.type != RepositoryType.GENERIC.name
        return if (isPackageScanPlanType || packageRule(rule)) {
            PackageIterator(packageClient, nodeClient, PackageIterator.PackageIteratePosition(rule))
        } else {
            NodeIterator(projectIdIterator, nodeClient, NodeIterator.NodeIteratePosition(rule))
        }
    }

    private fun modifyRule(scanPlan: ScanPlan, scanner: Scanner, rule: Rule.NestedRule): Rule {
        if (scanPlan.type == RepositoryType.GENERIC.name) {
            if (RuleUtil.getRepoNames(rule).isEmpty()) {
                // 未指定要扫描的仓库时限制只扫描GENERIC类型仓库
                addRepoNames(rule, scanPlan.projectId!!)
            }
            // 限制待扫描文件后缀
            return addFileNameExtRule(rule, scanner.supportFileNameExt)
        }
        return rule
    }

    /**
     * 添加文件名后缀过滤规则，不放到ScanPlan中，文件名后缀限制可能被移除或修改
     */
    private fun addFileNameExtRule(rule: Rule, supportFileNameExt: List<String>): Rule {
        if (supportFileNameExt.isEmpty()) {
            return rule
        }

        val fileNameExtensionRules = supportFileNameExt
            .map {
                if (it == "") {
                    // 空字符串表示支持无后缀文件，此处筛选出路径不包含.的文件
                    Rule.QueryRule(NodeDetail::fullPath.name, "^((?!\\.).)*$", OperationType.REGEX)
                } else {
                    Rule.QueryRule(NodeDetail::fullPath.name, ".$it", OperationType.SUFFIX)
                }
            }
            .toMutableList<Rule>()
        val fileNameExtRule = Rule.NestedRule(fileNameExtensionRules, Rule.NestedRule.RelationType.OR)

        if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.AND) {
            rule.rules.add(fileNameExtRule)
            return rule
        }
        return Rule.NestedRule(mutableListOf(rule, fileNameExtRule), Rule.NestedRule.RelationType.AND)
    }

    private fun addRepoNames(rule: Rule, projectId: String): Rule {
        val repoNames = Request
            .request { repositoryClient.listRepo(projectId, null, RepositoryType.GENERIC.name) }
            ?.map { it.name }
            ?: return rule

        val repoRule = Rule.QueryRule(NodeInfo::repoName.name, repoNames, OperationType.IN)
        return if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.AND) {
            rule.rules.add(repoRule)
            rule
        } else {
            Rule.NestedRule(mutableListOf(rule, repoRule))
        }
    }

    /**
     * 判断[rule]是否为请求package的rule
     */
    private fun packageRule(rule: Rule): Boolean {
        if (rule is Rule.QueryRule &&
            (rule.field == PackageSummary::key.name || rule.field == RuleArtifact::version.name)
        ) {
            return true
        }

        if (rule is Rule.NestedRule) {
            return rule.rules.any { packageRule(it) }
        }

        return false
    }
}
