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

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory

/**
 *
 */
open class NodeIterator(
    private val projectIdIterator: Iterator<String>,
    private val nodeClient: NodeClient,
    rule: Rule.NestedRule? = null,
    page: Int = INITIAL_PAGE,
    pageSize: Int = DEFAULT_PAGE_SIZE,
    index: Int = INITIAL_INDEX,
    resume: Boolean = false
) : PageableIterator<NodeIterator.Node>(page, pageSize, index, resume) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val rule: Rule?

    /**
     * 当前正在查询的projectId
     */
    private var projectId: String? = null

    init {
        this.rule = removeProjectIdRule(rule)
    }

    override fun nextPageData(page: Int, pageSize: Int): List<Node> {
        if (projectId == null) {
            projectId = if (projectIdIterator.hasNext()) {
                projectIdIterator.next()
            } else {
                throw SystemErrorException()
            }
        }

        val projectIdRule = createProjectIdRule(projectId!!, rule)
        // 获取下一页需要扫描的文件
        val queryModel = QueryModel(
            PageLimit(page, pageSize),
            Sort(listOf(AbstractMongoDao.ID), Sort.Direction.ASC),
            listOf(NodeDetail::sha256.name, NodeDetail::fullPath.name, NodeDetail::repoName.name),
            projectIdRule
        )
        val res = nodeClient.search(queryModel)
        if (res.isNotOk()) {
            logger.error("Search nodes failed: [${res.message}], queryModel:[$queryModel]")
            throw SystemErrorException()
        }

        val nodes = res.data!!.records
        if (nodes.isEmpty()) {
            // 当前project不存在需要扫描的文件，获取下一个要扫描的project
            if (projectIdIterator.hasNext()) {
                this.projectId = projectIdIterator.next()
            } else {
                return emptyList()
            }
            this.page = INITIAL_PAGE
            this.index = INITIAL_INDEX
            return nextPageData(this.page, this.index)
        }

        return nodes.map {
            val repoName = it[NodeDetail::repoName.name]!! as String
            val sha256 = it[NodeDetail::sha256.name]!! as String
            val fullPath = it[NodeDetail::fullPath.name]!! as String
            Node(projectId!!, repoName, fullPath, sha256)
        }
    }

    /**
     * 创建项目Id规则，最外层不存在projectId时候表示扫描所有
     *
     * @param projectId 设置规则匹配的项目
     * @param rule 匹配规则
     */
    private fun createProjectIdRule(projectId: String, rule: Rule?): Rule {
        val rules = ArrayList<Rule>(2)
        rules.add(Rule.QueryRule(NodeDetail::projectId.name, projectId, OperationType.EQ))
        rule?.let { rules.add(it) }
        return Rule.NestedRule(rules)
    }

    /**
     * 移除规则内所有和projectId有关的条件
     */
    private fun removeProjectIdRule(rule: Rule?): Rule? {
        when (rule) {
            is Rule.NestedRule -> {
                val rules = ArrayList<Rule>(rule.rules.size)
                rule.rules.forEach {
                    removeProjectIdRule(it)?.let { processedRule -> rules.add(processedRule) }
                }
                return rule.copy(rules = rules)
            }
            is Rule.QueryRule -> {
                if (rule.field == NodeDetail::projectId.name) {
                    return null
                }
                return rule
            }
            else -> {
                return rule
            }
        }
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

    data class Node(
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val sha256: String
    )
}

