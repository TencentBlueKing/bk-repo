/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.api

import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.fs.server.context.ReactiveArtifactContextHolder
import com.tencent.bkrepo.fs.server.model.Node
import com.tencent.bkrepo.fs.server.toNode
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import kotlinx.coroutines.reactor.awaitSingle
import kotlin.reflect.full.declaredMemberProperties

class NodeClient(
    private val repositoryClient: RRepositoryClient,
    private val genericClient: RGenericClient,
) {

    suspend fun getNodeDetail(
        projectId: String,
        repo: RepositoryDetail,
        fullPath: String,
        category: String,
    ): NodeDetail? {
        return if (category == RepositoryCategory.LOCAL.name) {
            repositoryClient.getNodeDetail(
                projectId = projectId,
                repoName = repo.name,
                fullPath = fullPath
            ).awaitSingle().data
        } else if (repo.type == RepositoryType.GENERIC) {
            genericClient.getNodeDetail(
                projectId = projectId,
                repoName = repo.name,
                fullPath = fullPath
            ).awaitSingle().data
        } else {
            null
        }
    }


    suspend fun listNodes(
        projectId: String,
        repo: RepositoryDetail,
        path: String,
        option: NodeListOption
    ): List<Node>? {
        val nodes = if (ReactiveArtifactContextHolder.getRepoDetail().isLocalRepo()) {
            repositoryClient.listNodePage(
                path = path,
                projectId = projectId,
                repoName = repo.name,
                option = option
            ).awaitSingle().data?.records?.map { it.toNode() }?.toList()
        } else if (repo.type == RepositoryType.GENERIC) {
            val builder = NodeQueryBuilder()
                .page(option.pageNumber, option.pageSize)
                .select(*select)
                .projectId(projectId)
                .repoName(repo.name)
                .path(path.ensureSuffix("/"))
            if (!option.includeFolder) {
                builder.excludeFolder()
            }
            return genericClient.search(projectId, repo.name, builder.build()).awaitSingle().data
                ?.map { (it as Map<String, Any?>).toNode() }
                ?.toList()
                ?.filterNotNull()
        } else {
            null
        }
        return nodes
    }

    private fun RepositoryDetail.isLocalRepo(): Boolean {
        val repoConfiguration = configuration
        return category == RepositoryCategory.LOCAL ||
                repoConfiguration is CompositeConfiguration && repoConfiguration.proxy.channelList.isEmpty()
    }

    companion object {
        private val select = Node::class.declaredMemberProperties.map { it.name }.toTypedArray()
    }
}
