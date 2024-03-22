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

package com.tencent.bkrepo.common.artifact.cache.service.impl

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactPreloadPlanDao
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanGenerateParam
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanGenerator
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanService
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadStrategyService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Service
class ArtifactPreloadPlanServiceImpl(
    private val nodeClient: NodeClient,
    private val repositoryClient: RepositoryClient,
    private val strategyService: ArtifactPreloadStrategyService,
    private val preloadPlanDao: ArtifactPreloadPlanDao,
    private val preloadStrategies: Map<String, ArtifactPreloadPlanGenerator>,
) : ArtifactPreloadPlanService {
    private val repositoryCache = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build(CacheLoader.from<String, RepositoryInfo> { getRepo(it) })

    override fun createPlan(credentialsKey: String?, sha256: String) {
        val option = NodeListOption(pageSize = MAX_PAGE_SIZE, includeFolder = false)
        val res = nodeClient.listPageNodeBySha256(sha256, option)
        if (res.data?.records?.size == MAX_PAGE_SIZE) {
            // 限制查询出来的最大node数量，避免预加载计划创建时间过久
            throw RuntimeException("exceed max page size[$MAX_PAGE_SIZE]")
        }
        val strategyCache = HashMap<String, List<ArtifactPreloadStrategy>>()
        res.data?.records?.forEach { node ->
            val repo = repositoryCache.get(buildRepoId(node.projectId, node.repoName))
            if (repo.storageCredentialsKey == credentialsKey) {
                val strategies = strategyCache.getOrPut(buildRepoId(node.projectId, node.repoName)) {
                    strategyService.list(node.projectId, node.repoName)
                }
                strategies.forEach { strategy -> createPlan(strategy, node, repo.storageCredentialsKey) }
            }
        }
    }

    private fun createPlan(strategy: ArtifactPreloadStrategy, node: NodeInfo, credentialsKey: String?) {
        if (strategy.fullPathRegex!!.toRegex().matches(node.fullPath)) {
            logger.info("${node.projectId}/${node.repoName}${node.fullPath} not match preload strategy")
            return
        }

        // check created date
        val createdDate = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        val now = LocalDateTime.now()
        if (Duration.between(createdDate, now).seconds > strategy.recentSeconds!!) {
            logger.info("${node.projectId}/${node.repoName}${node.fullPath} cant be preload because of duration")
            return
        }

        val plan = preloadStrategies[strategy.type]?.let { generator ->
            val param = ArtifactPreloadPlanGenerateParam(
                projectId = node.projectId,
                repoName = node.repoName,
                credentialsKey = credentialsKey,
                fullPath = node.fullPath,
                sha256 = node.sha256!!,
                strategy = strategy
            )
            generator.generate(param)
        }
        plan?.let { preloadPlanDao.insert(it.toPo()) }
    }

    private fun getRepo(key: String): RepositoryInfo {
        val repoId = key.split(REPO_ID_DELIMITERS)
        require(repoId.size == 2)
        return repositoryClient.getRepoInfo(repoId[0], repoId[1]).data
            ?: throw RuntimeException("repo[$key] was not exists")
    }

    private fun buildRepoId(projectId: String, repoName: String) = "${projectId}$REPO_ID_DELIMITERS${repoName}"

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactPreloadPlanServiceImpl::class.java)
        private const val REPO_ID_DELIMITERS = "/"
        private const val MAX_PAGE_SIZE = 1000
    }
}
