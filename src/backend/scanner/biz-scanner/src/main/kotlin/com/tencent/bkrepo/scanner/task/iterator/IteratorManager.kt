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

package com.tencent.bkrepo.scanner.task.iterator

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.scanner.pojo.ScanTask
import org.springframework.stereotype.Component

/**
 * 迭代器管理器
 * TODO 实现迭代进度管理
 */
@Component
class IteratorManager(
    private val projectClient: ProjectClient,
    private val nodeClient: NodeClient
) {
    /**
     * 创建待扫描文件迭代器
     *
     * @param scanTask 扫描任务
     * @param resume 是否从之前的扫描进度恢复
     */
    fun createNodeIterator(scanTask: ScanTask, resume: Boolean = false): NodeIterator {
        val rule = scanTask.rule
        val projectIdIterator = if (rule is Rule.NestedRule) {
            val projectIds = projectIdsFromRule(rule)
            projectIds.iterator()
        } else {
            ProjectIdIterator(projectClient)
        }
        return NodeIterator(projectIdIterator, nodeClient)
    }

    /**
     * 如果指定要扫描的projectId，必须relation为AND，在nestedRule里面的第一层rule包含projectId的匹配条件
     */
    private fun projectIdsFromRule(rule: Rule.NestedRule): List<String> {
        val projectIds = ArrayList<String>()
        if (rule.relation != Rule.NestedRule.RelationType.AND) {
            return emptyList()
        } else {
            rule.rules
                .asSequence()
                .filterIsInstance(Rule.QueryRule::class.java)
                .filter { it.field == NodeDetail::projectId.name }
                .forEach {
                    if (it.operation == OperationType.EQ) {
                        projectIds.add(it.value as String)
                    } else if (it.operation == OperationType.IN) {
                        projectIds.addAll(it.value as Collection<String>)
                    }
                }
        }
        return projectIds
    }
}
