/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.search.packages

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.dao.PackageVersionDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component

/**
 * 版本Checksum规则拦截器, 查询包版本Checksum需要在嵌套查询规则列表中指定projectId和repoType条件，且均为EQ操作
 */
@Component
class VersionChecksumRuleInterceptor(
    override val packageVersionDao: PackageVersionDao,
    private val nodeDao: NodeDao,
    private val repositoryService: RepositoryService
) : VersionRuleInterceptor(packageVersionDao) {

    override fun match(rule: Rule): Boolean {
        return rule is Rule.QueryRule && rule.field in CHECKSUM_FIELDS
    }

    override fun getVersionCriteria(rule: Rule, context: PackageQueryContext): Criteria {
        with(rule as Rule.QueryRule) {
            if (operation != OperationType.EQ) {
                throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "$field only support EQ operation type.")
            }
            val projectId = context.findProjectId()
            val repoType = context.findRepoType().toUpperCase()
            val userId = SecurityUtils.getUserId()
            // 筛选有权限的仓库列表, docker和oci仓库互相兼容
            val repoList = if (repoType in listOf(RepositoryType.DOCKER.name, RepositoryType.OCI.name)) {
                val dockerRepoList = repositoryService.listPermissionRepo(
                    userId, projectId, RepoListOption(type = RepositoryType.DOCKER.name)
                )
                val ociRepoList = repositoryService.listPermissionRepo(
                    userId, projectId, RepoListOption(type = RepositoryType.OCI.name)
                )
                dockerRepoList + ociRepoList
            } else
                repositoryService.listPermissionRepo(userId, projectId, RepoListOption(type = repoType))
            // 从有权限的仓库列表查询符合checksum规则的节点
            val nodeQuery = Query(
                Criteria.where(TNode::projectId.name).isEqualTo(projectId)
                    .and(TNode::repoName.name).inValues(repoList.map { it.name })
                    .and(field).isEqualTo(value.toString())
            )
            val fullPaths = queryRecords(nodeQuery) { q -> nodeDao.find(q) }.map { it.fullPath }
            // 转换为package_version查询条件
            return if (repoType == RepositoryType.DOCKER.name || repoType == RepositoryType.OCI.name) {
                Criteria.where(TPackageVersion::manifestPath.name).inValues(fullPaths)
            } else {
                Criteria.where(TPackageVersion::artifactPath.name).inValues(fullPaths)
            }
        }
    }

    companion object {
        private val CHECKSUM_FIELDS = arrayOf(
            TNode::sha256.name,
            TNode::md5.name
        )
    }
}
