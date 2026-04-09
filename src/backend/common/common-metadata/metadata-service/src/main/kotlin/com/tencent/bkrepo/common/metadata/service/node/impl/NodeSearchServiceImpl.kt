/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.search.node.NodeQueryContext
import com.tencent.bkrepo.common.metadata.search.node.NodeQueryInterpreter
import com.tencent.bkrepo.common.metadata.service.metadata.impl.MetadataLabelCacheService
import com.tencent.bkrepo.common.metadata.service.node.NodeSearchService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.separation.SeparationDataService
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.SeparationUtils
import com.tencent.bkrepo.common.mongo.i18n.ZoneIdContext
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.software.ProjectPackageOverview
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * 节点自定义查询服务实现类
 */
@Suppress("UNCHECKED_CAST")
@Service
@Conditional(SyncCondition::class)
class NodeSearchServiceImpl(
    private val nodeDao: NodeDao,
    private val nodeQueryInterpreter: NodeQueryInterpreter,
    private val repositoryService: RepositoryService,
    private val repositoryProperties: RepositoryProperties,
    private val metadataLabelCacheService: MetadataLabelCacheService,
    private val separationDataService: SeparationDataService,
    private val dataSeparationConfig: DataSeparationConfig,
) : NodeSearchService {

    override fun search(queryModel: QueryModel): Page<Map<String, Any?>> {
        val context = nodeQueryInterpreter.interpret(queryModel) as NodeQueryContext
        return doQuery(context)
    }

    override fun searchWithoutCount(queryModel: QueryModel): Page<Map<String, Any?>> {
        val context = nodeQueryInterpreter.interpret(queryModel) as NodeQueryContext
        return doQueryWithoutCount(context)
    }

    override fun nodeOverview(
        userId: String,
        projectId: String,
        name: String,
        exRepo: String?
    ): List<ProjectPackageOverview> {
        val repos = repositoryService.listPermissionRepo(
            userId = userId,
            projectId = projectId,
            option = RepoListOption(
                type = RepositoryType.GENERIC.name
            )
        ).map { it.name }
        val genericRepos = if (exRepo != null && exRepo.isNotBlank()) {
            repos.filter { it !in (exRepo.split(',')) }
        } else repos

        if (genericRepos.isEmpty()) {
            return listOf(
                ProjectPackageOverview(
                    projectId = projectId,
                    repos = mutableSetOf(),
                    sum = 0L
                )
            )
        }
        return transTree(projectId, genericRepos)
    }

    private fun transTree(projectId: String, repoNamelist: List<String>): List<ProjectPackageOverview> {
        val projectSet = mutableSetOf<ProjectPackageOverview>()
        projectSet.add(
            ProjectPackageOverview(
                projectId = projectId,
                repos = mutableSetOf(),
                sum = 0L
            )
        )
        repoNamelist.map { pojo ->
            val repoOverview = ProjectPackageOverview.RepoPackageOverview(
                repoName = pojo,
                packages = 0L
            )
            projectSet.first().repos.add(repoOverview)
            projectSet.first().sum += 0L
        }
        return projectSet.toList()
    }

    private fun queryList(query: Query): List<MutableMap<String, Any?>> {
        val nodeList: List<MutableMap<String, Any?>>
        val time = measureTimeMillis {
            nodeList = nodeDao.find(query, MutableMap::class.java) as List<MutableMap<String, Any?>>
        }
        if (time > repositoryProperties.slowLogTimeThreshold) {
            logger.warn("search node slow log, " +
                "query[${query.queryObject.toJsonString().replace(System.lineSeparator(), "")}], " +
                "cost ${HumanReadable.time(time, TimeUnit.MILLISECONDS)}")
        }
        return postProcessNodeList(nodeList)
    }

    private fun postProcessNodeList(nodeList: List<MutableMap<String, Any?>>): List<MutableMap<String, Any?>> {
        val projectId = nodeList.firstOrNull()?.get(NodeInfo::projectId.name)?.toString()
        val metadataLabels = projectId?.let { metadataLabelCacheService.listAll(it) } ?: emptyList()
        // metadata格式转换，并排除id字段
        nodeList.forEach {
            it.remove("_id")
            it[NodeInfo::createdDate.name]?.let { createDate ->
                it[TNode::createdDate.name] = convertDateTime(createDate)
            }
            it[NodeInfo::lastModifiedDate.name]?.let { lastModifiedDate ->
                it[TNode::lastModifiedDate.name] = convertDateTime(lastModifiedDate)
            }
            it[NodeInfo::deleted.name]?.let { deleted ->
                it[TNode::deleted.name] = convertDateTime(deleted)
            }
            it[NodeInfo::metadata.name]?.let { metadata ->
                it[NodeInfo::metadata.name] = MetadataUtils.convert(metadata as List<Map<String, Any>>)
                it[NodeInfo::nodeMetadata.name] = MetadataUtils.convertToMetadataModel(metadata, metadataLabels)
            }
        }
        return nodeList
    }

    private fun doQuery(context: NodeQueryContext): Page<Map<String, Any?>> {
        val query = context.mongoQuery
        val pageSize = query.limit
        val skipCount = query.skip
        val countQuery = Query.of(query).limit(0).skip(0)
        val separationEnabled = isSeparationEnabled(context)

        // 先查热表
        val hotNodes = queryList(query)

        val hotCount = nodeDao.count(countQuery)
        val coldCount = if (separationEnabled) separationDataService.countColdNodes(countQuery) else 0L
        val totalRecords = hotCount + coldCount

        // 热表不足一页时从冷表补充
        val coldNodes = if (separationEnabled && hotNodes.size < pageSize) {
            val coldSkip = maxOf(0L, skipCount - hotCount)
            val coldLimit = pageSize - hotNodes.size
            postProcessNodeList(separationDataService.searchColdNodes(countQuery, coldSkip, coldLimit))
        } else emptyList()

        val pageNumber = if (pageSize == 0) 0 else (skipCount / pageSize).toInt()
        return Page(pageNumber + 1, pageSize, totalRecords, hotNodes + coldNodes)
    }

    private fun doQueryWithoutCount(context: NodeQueryContext): Page<Map<String, Any?>> {
        val query = context.mongoQuery
        val pageSize = query.limit
        val skipCount = query.skip
        val separationEnabled = isSeparationEnabled(context)

        // 先查热表
        val hotNodes = queryList(query)

        // 热表不足一页说明热表数据已耗尽，直接从冷表头部补充，无需 count
        val coldNodes = if (separationEnabled && hotNodes.size < pageSize) {
            val coldQuery = Query.of(query).limit(0).skip(0)
            val coldLimit = pageSize - hotNodes.size
            postProcessNodeList(separationDataService.searchColdNodes(coldQuery, 0L, coldLimit))
        } else emptyList()

        val pageNumber = if (pageSize == 0) 0 else (skipCount / pageSize).toInt()
        return Page(pageNumber + 1, pageSize, 0, hotNodes + coldNodes)
    }

    private fun isSeparationEnabled(context: NodeQueryContext): Boolean {
        if (dataSeparationConfig.specialSeparateRepos.isEmpty()) return false
        val allowedPlatformIds = dataSeparationConfig.separationQueryPlatformIds
        if (allowedPlatformIds.isEmpty() || SecurityUtils.getPlatformId() !in allowedPlatformIds) return false
        val projectId = try { context.findProjectId() } catch (e: Exception) { return false }
        // repoList由repoType规则解析后填充；repoName规则则从query rules中提取
        val repoNames = context.repoList?.map { it.name }
            ?: findRepoNamesFromRule(context.queryModel.rule)
        return repoNames.any {
            SeparationUtils.matchesConfigRepos("$projectId/$it", dataSeparationConfig.specialSeparateRepos)
        }
    }

    private fun findRepoNamesFromRule(rule: Rule): List<String> {
        return when {
            rule is Rule.QueryRule && rule.field == TNode::repoName.name -> when (val v = rule.value) {
                is List<*> -> v.filterNotNull().map { it.toString() }
                else -> listOf(v.toString())
            }
            rule is Rule.NestedRule -> rule.rules.flatMap { findRepoNamesFromRule(it) }
            else -> emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeSearchServiceImpl::class.java)
        fun convertDateTime(value: Any): LocalDateTime? {
            return if (value is Date) {
                LocalDateTime.ofInstant(
                    value.toInstant(),
                    ZoneIdContext.getZoneId()
                )
            } else null
        }
    }
}
