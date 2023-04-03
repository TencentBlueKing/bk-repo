/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.search.software.interceptor

import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.search.common.CommonQueryContext
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component

/**
 * 仓库名称规则拦截器
 * 在 service 层只加载系统级仓库，这里不再做权限校验
 */
@Component
class SoftwareRepoNameRuleInterceptor(
    private val repositoryService: RepositoryService
) : QueryRuleInterceptor {

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field == NodeInfo::repoName.name
    }

    override fun intercept(rule: Rule, context: QueryContext): Criteria {
        with(rule as Rule.QueryRule) {
            require(context is CommonQueryContext)
            val queryRule = when (operation) {
                OperationType.EQ -> {
                    handleRepoNameEq(context.findProjectId(), value.toString())
                }
                OperationType.IN -> {
                    val listValue = value
                    require(listValue is List<*>)
                    if (listValue.size == 1) {
                        Rule.QueryRule(NodeInfo::repoName.name, listValue.first() as String, OperationType.EQ)
                    } else {
                        Rule.QueryRule(NodeInfo::repoName.name, listValue as List<String>, OperationType.IN)
                    }
                }
                else -> throw IllegalArgumentException("RepoName only support EQ and IN operation type.")
            }.toFixed()
            return context.interpreter.resolveRule(queryRule, context)
        }
    }

    private fun handleRepoNameEq(
        projectId: String,
        value: String
    ): Rule.QueryRule {
        val repoInfo = repositoryService.getRepoInfo(projectId, value)
        if (repoInfo?.category == RepositoryCategory.VIRTUAL) {
            val memberList = (repoInfo.configuration as VirtualConfiguration).repositoryList.map { it.name }
            if (memberList.isNotEmpty()) {
                return handleRepoNameIn(projectId, memberList)
            }
        }
        return Rule.QueryRule(NodeInfo::repoName.name, value, OperationType.EQ)
    }

    private fun handleRepoNameIn(
        projectId: String,
        value: List<String>
    ): Rule.QueryRule {
        val repoNameList = value.filter { isSystemOrPublicRepo(projectId, it) }
        return if (repoNameList.size == 1) {
            Rule.QueryRule(NodeInfo::repoName.name, repoNameList.first(), OperationType.EQ)
        } else {
            Rule.QueryRule(NodeInfo::repoName.name, repoNameList, OperationType.IN)
        }
    }

    private fun isSystemOrPublicRepo(
        projectId: String,
        repoName: String
    ): Boolean {
        return repositoryService.getRepoInfo(projectId, repoName)?.run {
            public || configuration.settings["system"] as? Boolean ?: false
        } ?: false
    }
}
