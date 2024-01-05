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

package com.tencent.bkrepo.repository.search.common

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.interceptor.QueryContext
import com.tencent.bkrepo.common.query.interceptor.QueryRuleInterceptor
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component

/**
 * 仓库类型规则拦截器
 *
 * 条件构造器中传入条件是`repoName`，过滤无权限的仓库
 */
@Component
class RepoNameRuleInterceptor(
    private val permissionManager: PermissionManager,
    private val repositoryService: RepositoryService,
    private val servicePermissionClient: ServicePermissionClient,
) : QueryRuleInterceptor {

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field == NodeInfo::repoName.name
    }

    override fun intercept(rule: Rule, context: QueryContext): Criteria {
        with(rule as Rule.QueryRule) {
            require(context is CommonQueryContext)
            val projectId = context.findProjectId()
            val queryRule = when (operation) {
                OperationType.EQ -> {
                    handleRepoNameEq(projectId, value.toString())
                }
                OperationType.IN -> {
                    val listValue = value
                    require(listValue is List<*>)
                    handleRepoNameIn(projectId, listValue, context)
                }
                OperationType.NIN -> {
                    val listValue = value
                    require(listValue is List<*>)
                    handleRepoNameNin(projectId, listValue)
                }
                else -> throw IllegalArgumentException("RepoName only support EQ IN and NIN operation type.")
            }
            context.permissionChecked = true
            return context.interpreter.resolveRule(queryRule, context)
        }
    }

    private fun handleRepoNameEq(
        projectId: String,
        value: String
    ): Rule {
        if (!hasRepoPermission(projectId, value)) {
            throw PermissionException()
        }
        return buildRule(projectId, listOf(value))
    }

    private fun handleRepoNameIn(
        projectId: String,
        value: List<*>,
        context: CommonQueryContext
    ): Rule {
        val repoNameList = if (context.repoList != null) {
            context.repoList!!.filter { hasRepoPermission(projectId, it.name, it.public) }.map { it.name }
        } else {
            value.filter { hasRepoPermission(projectId, it.toString()) }.map { it.toString() }
        }
        return buildRule(projectId, repoNameList)
    }

    private fun handleRepoNameNin(
        projectId: String,
        value: List<*>,
    ): Rule {
        val userId = SecurityUtils.getUserId()
        val repoNameList = repositoryService.listPermissionRepo(
            userId = userId,
            projectId = projectId,
            option = RepoListOption()
        )?.map { it.name }?.filter { repo -> repo !in (value.map { it.toString() }) }
        return buildRule(projectId, repoNameList)
    }

    private fun buildRule(projectId: String, repoNames: List<String>): Rule {
        if (repoNames.isEmpty()) {
            throw PermissionException(
                "${SecurityUtils.getUserId()} hasn't any PermissionRepo in project [$projectId], " +
                        "or project [$projectId] hasn't any repo"
            )
        }
        if (repoNames.size == 1) {
            // 单仓库查询
            return buildRule(projectId, repoNames.first())
        }

        // 跨仓库查询
        val rules = repoNames.mapTo(ArrayList(repoNames.size)) { repoName -> buildRule(projectId, repoName) }
        return if (rules.all { it !is Rule.NestedRule }) {
            // 不需要校验路径权限
            Rule.QueryRule(NodeInfo::repoName.name, repoNames, OperationType.IN).toFixed()
        } else {
            // 存在某个仓库需要进行路径权限校验
            Rule.NestedRule(rules, Rule.NestedRule.RelationType.OR)
        }
    }

    private fun buildRule(projectId: String, repoName: String): Rule {
        val repoRule = Rule.QueryRule(NodeInfo::repoName.name, repoName, OperationType.EQ).toFixed()
        return listNoPermissionPath(projectId, repoName)?.let {
            val pathRules = it.flatMapTo(ArrayList(it.size)) { path ->
                listOf(
                    Rule.QueryRule(NodeInfo::fullPath.name, path.ensureSuffix("/"), OperationType.PREFIX) as Rule,
                    Rule.QueryRule(NodeInfo::fullPath.name, path, OperationType.EQ) as Rule,
                )
            }
            val pathRule = Rule.NestedRule(pathRules, Rule.NestedRule.RelationType.NOR)
            Rule.NestedRule(mutableListOf(repoRule, pathRule))
        } ?: repoRule
    }

    private fun listNoPermissionPath(projectId: String, repoName: String): List<String>? {
        val userId = SecurityUtils.getUserId()
        val result = servicePermissionClient.listPermissionPath(userId, projectId, repoName).data!!
        if (result.status) {
            val paths = result.path.flatMap {
                require(it.key == OperationType.NIN)
                it.value
            }
            logger.info("user[$userId] does not have permission to $paths of [$projectId/$repoName], will be filtered")
            return paths.ifEmpty { null }
        }
        return null
    }

    private fun hasRepoPermission(
        projectId: String,
        repoName: String,
        repoPublic: Boolean? = null
    ): Boolean {
        if (SecurityUtils.isServiceRequest()) {
            return true
        }
        return try {
            permissionManager.checkRepoPermission(
                action = PermissionAction.READ,
                projectId = projectId,
                repoName = repoName,
                public = repoPublic,
                anonymous = true
            )
            true
        } catch (ignored: PermissionException) {
            false
        } catch (ignored: RepoNotFoundException) {
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepoNameRuleInterceptor::class.java)
    }
}
