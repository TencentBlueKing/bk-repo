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
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactPreloadPlanDao
import com.tencent.bkrepo.common.artifact.cache.model.TArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan.Companion.STATUS_PENDING
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan.Companion.toDto
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanGenerateParam
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanGenerator
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanService
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadStrategyService
import com.tencent.bkrepo.common.artifact.cache.service.PreloadPlanExecutor
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.metrics.ArtifactCacheMetrics
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ArtifactPreloadPlanServiceImpl(
    private val nodeService: NodeService,
    private val repositoryService: RepositoryService,
    private val strategyService: ArtifactPreloadStrategyService,
    private val preloadPlanDao: ArtifactPreloadPlanDao,
    private val preloadStrategies: Map<String, ArtifactPreloadPlanGenerator>,
    private val properties: ArtifactPreloadProperties,
    private val preloadPlanExecutor: PreloadPlanExecutor,
    private val preloadProperties: ArtifactPreloadProperties,
    private val cacheMetrics: ArtifactCacheMetrics,
) : ArtifactPreloadPlanService {
    private val repositoryCache = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build(CacheLoader.from<String, RepositoryInfo> { getRepo(it) })
    private val listener = DefaultPreloadListener(preloadPlanDao, cacheMetrics)

    override fun createPlan(request: ArtifactPreloadPlanCreateRequest): ArtifactPreloadPlan {
        with(request) {
            val now = LocalDateTime.now()
            if (System.currentTimeMillis() - executeTime > preloadProperties.planTimeout.toMillis()) {
                throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "execute time[$executeTime] too earlier")
            }

            val node = getNode(projectId, repoName, fullPath)
            val repo = repositoryCache.get(buildRepoId(projectId, repoName))
            return preloadPlanDao.insert(
                TArtifactPreloadPlan(
                    id = null,
                    createdDate = now,
                    lastModifiedDate = now,
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    sha256 = node.sha256!!,
                    size = node.size,
                    credentialsKey = repo.storageCredentialsKey,
                    executeTime = executeTime,
                    status = STATUS_PENDING,
                )
            ).toDto()
        }
    }

    override fun generatePlan(credentialsKey: String?, sha256: String) {
        if (!properties.enabled) {
            return
        }
        val nodes = nodeService.listNodeBySha256(
            sha256 = sha256,
            limit = properties.maxNodes,
            includeMetadata = false,
            includeDeleted = false,
            tillLimit = false
        )
        if (nodes.size >= properties.maxNodes) {
            // 限制查询出来的最大node数量，避免预加载计划创建时间过久
            logger.warn("nodes of sha256[$sha256] exceed max page size[${properties.maxNodes}]")
            return
        } else if (nodes.isEmpty()) {
            logger.debug("nodes of sha256[$sha256] found")
            return
        }
        // node属于同一项目仓库的概率较大，缓存避免频繁查询策略
        val strategyCache = HashMap<String, List<ArtifactPreloadStrategy>>()
        val plans = ArrayList<ArtifactPreloadPlan>()
        // 查询node是否有匹配的策略，并根据策略生成预加载计划
        for (node in nodes) {
            val repo = repositoryCache.get(buildRepoId(node.projectId, node.repoName))
            if (repo.storageCredentialsKey != credentialsKey) {
                logger.debug("credentialsKey of repo[${repo.name}] not match dst credentialsKey[${credentialsKey}]")
                continue
            }
            val strategies = strategyCache.getOrPut(buildRepoId(node.projectId, node.repoName)) {
                strategyService.list(node.projectId, node.repoName)
            }
            if (strategies.isEmpty()) {
                logger.debug("preload strategy of repo[${repo.projectId}/${repo.name}] is empty")
            }
            strategies.forEach { strategy ->
                matchAndGeneratePlan(strategy, node, repo.storageCredentialsKey)?.let { plans.add(it) }
            }
        }
        // 保存计划
        if (plans.isNotEmpty()) {
            preloadPlanDao.insert(plans.map { it.toPo() })
        }
    }

    override fun deletePlan(projectId: String, repoName: String, id: String) {
        if (preloadPlanDao.remove(projectId, repoName, id).deletedCount == 0L) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        }
    }

    override fun deletePlan(projectId: String, repoName: String) {
        preloadPlanDao.remove(projectId, repoName)
    }

    override fun executePlans() {
        val plans = preloadPlanDao.listReadyPlans(properties.preloadConcurrency)
        logger.info("${plans.size} plans is ready to execute")
        var count = 0
        for (plan in plans) {
            if (!preloadPlanExecutor.execute(plan.toDto(), listener)) {
                break
            }
            count++
        }
        logger.info("$count plans is executed")
    }

    override fun plans(projectId: String, repoName: String, pageRequest: PageRequest): Page<ArtifactPreloadPlan> {
        val records = preloadPlanDao.page(projectId, repoName, pageRequest).map { it.toDto() }
        return Pages.ofResponse(pageRequest, records.size.toLong(), records)
    }

    private fun matchAndGeneratePlan(
        strategy: ArtifactPreloadStrategy,
        node: NodeInfo,
        credentialsKey: String?
    ): ArtifactPreloadPlan? {
        // 检查是否匹配筛选规则
        val createdDateTime = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        val now = LocalDateTime.now()
        val sizeNotMatch = node.size < strategy.minSize
        val pathNotMatch = !strategy.fullPathRegex.toRegex().matches(node.fullPath)
        val createTimeNotMatch = Duration.between(createdDateTime, now).seconds > strategy.recentSeconds
        if (sizeNotMatch || pathNotMatch || createTimeNotMatch) {
            logger.info(
                "${node.projectId}/${node.repoName}${node.fullPath} not match preload strategy, " +
                        "node size[${node.size}], node createdDateTime[$createdDateTime]"
            )
            return null
        }

        // 生成预加载计划
        return preloadStrategies[strategy.type]?.let { generator ->
            val param = ArtifactPreloadPlanGenerateParam(
                projectId = node.projectId,
                repoName = node.repoName,
                credentialsKey = credentialsKey,
                fullPath = node.fullPath,
                sha256 = node.sha256!!,
                size = node.size,
                strategy = strategy
            )
            logger.info("generate preload plan for sha256[${node.sha256}]")
            generator.generate(param)
        }
    }

    private fun getRepo(key: String): RepositoryInfo {
        val repoId = key.split(REPO_ID_DELIMITERS)
        require(repoId.size == 2)
        return repositoryService.getRepoInfo(repoId[0], repoId[1])
            ?: throw RuntimeException("repo[$key] was not exists")
    }

    private fun buildRepoId(projectId: String, repoName: String) = "${projectId}$REPO_ID_DELIMITERS$repoName"

    private fun getNode(projectId: String, repoName: String, fullPath: String): NodeDetail {
        val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
            ?: throw ArtifactNotFoundException("$projectId/$repoName$fullPath not found")
        if (node.folder) {
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "folder is unsupported")
        }
        if (node.sha256 == FAKE_SHA256 || node.size == 0L) {
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "fake node is unsupported")
        }

        return node
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactPreloadPlanServiceImpl::class.java)
        private const val REPO_ID_DELIMITERS = "/"
    }
}
