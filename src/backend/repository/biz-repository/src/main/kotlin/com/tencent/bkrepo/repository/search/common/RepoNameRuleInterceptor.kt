/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.  
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.repository.search.common

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.http.SecurityUtils
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component

/**
 * 仓库类型规则拦截器
 *
 * 条件构造器中传入条件是`repoName`，过滤无权限的仓库
 */
@Component
class RepoNameRuleInterceptor : QueryRuleInterceptor {

    @Autowired
    private lateinit var permissionManager: PermissionManager

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field == NodeInfo::repoName.name
    }

    override fun intercept(rule: Rule, context: QueryContext): Criteria {
        with(rule as Rule.QueryRule) {
            val userId = SecurityUtils.getUserId()
            val projectId = (context as CommonQueryContext).findProjectId()
            val queryRule = when (operation) {
                OperationType.EQ -> handleRepoNameEq(userId, projectId, value.toString())
                OperationType.IN -> handleRepoNameIn(userId, projectId, value as List<*>, context)
                else -> throw IllegalArgumentException("RepoName only support EQ and IN operation type.")
            }.toFixed()
            return context.interpreter.resolveRule(queryRule, context)
        }
    }

    private fun handleRepoNameEq(
        userId: String,
        projectId: String,
        value: String
    ): Rule.QueryRule {
        hasRepoPermission(userId, projectId, value)
        return Rule.QueryRule(NodeInfo::repoName.name, value, OperationType.EQ)
    }

    private fun handleRepoNameIn(
        userId: String,
        projectId: String,
        value: List<*>,
        context: CommonQueryContext
    ): Rule.QueryRule {
        val repoNameList = if (context.repoList != null) {
            context.repoList!!.filter { hasRepoPermission(userId, projectId, it.name, it.public) }.map { it.name }
        } else {
            value.filter { hasRepoPermission(userId, projectId, it.toString()) }.map { it.toString() }
        }
        return if (repoNameList.size == 1) {
            Rule.QueryRule(NodeInfo::repoName.name, repoNameList.first(), OperationType.EQ)
        } else {
            Rule.QueryRule(NodeInfo::repoName.name, repoNameList, OperationType.IN)
        }
    }

    private fun hasRepoPermission(
        userId: String,
        projectId: String,
        repoName: String,
        repoPublic: Boolean? = null
    ): Boolean {
        return try {
            permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName, repoPublic)
            true
        } catch (ignored: Exception) {
            false
        }
    }
}
