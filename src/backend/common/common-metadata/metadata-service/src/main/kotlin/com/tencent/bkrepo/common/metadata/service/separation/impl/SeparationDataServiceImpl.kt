/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.model.TSeparationPackage
import com.tencent.bkrepo.common.metadata.model.TSeparationPackageVersion
import com.tencent.bkrepo.common.metadata.service.separation.SeparationDataService
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import com.tencent.bkrepo.common.metadata.util.SeparationQueryHelper
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Suppress("UNCHECKED_CAST")

@Service
class SeparationDataServiceImpl(
    private val separationPackageVersionDao: SeparationPackageVersionDao,
    private val separationPackageDao: SeparationPackageDao,
    private val separationNodeDao: SeparationNodeDao,
    private val separationTaskService: SeparationTaskService
) : SeparationDataService {

    override fun findNodeInfo(projectId: String, repoName: String, fullPath: String): NodeInfo? {
        val separationDates = separationTaskService.findDistinctSeparationDate(projectId, repoName)
        if (separationDates.isEmpty()) return null
        // 按时间倒序遍历，优先命中最近降冷的数据
        separationDates.sortedDescending().forEach {
            val tNode = separationNodeDao.findOne(
                SeparationQueryHelper.fullPathQuery(
                    projectId, repoName, fullPath, it
                )
            )
            if (tNode != null) {
                return convert(tNode)
            }
        }
        return null
    }

    override fun findPackageVersion(
        projectId: String, repoName: String, packageKey: String, versionName: String
    ): PackageVersion? {
        val separationDates = separationTaskService.findDistinctSeparationDate(projectId, repoName)
        if (separationDates.isEmpty()) return null
        val packageInfo = separationPackageDao.findByKey(projectId, repoName, packageKey) ?: return null
        separationDates.forEach {
            val versionInfo = separationPackageVersionDao.findByName(packageInfo.id!!, versionName, it)
            if (versionInfo != null) {
                return convert(versionInfo)
            }
        }
        return null
    }

    override fun hasColdNodeUnderPath(projectId: String, repoName: String, fullPath: String): Boolean {
        val separationDates = separationTaskService.findDistinctSeparationDate(projectId, repoName)
        if (separationDates.isEmpty()) return false
        val option = NodeListOption(pageNumber = 0, pageSize = 1, includeFolder = false, deep = true)
        return separationDates.any { date ->
            val query = SeparationQueryHelper.nodeListQuery(projectId, repoName, fullPath, option, date)
            separationNodeDao.exists(query)
        }
    }

    override fun listNodePage(
        projectId: String, repoName: String,
        fullPath: String, option: NodeListOption,
        separationDate: LocalDateTime,
    ): Page<NodeInfo> {
        checkNodeListOption(option)
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        val query = SeparationQueryHelper.nodeListQuery(projectId, repoName, fullPath, option, separationDate)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val records = separationNodeDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, 0, records)
    }

    override fun listPackagePage(
        projectId: String, repoName: String,
        option: PackageListOption,
    ): Page<PackageSummary> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        Preconditions.checkArgument(pageSize >= 0, "pageSize")
        val query = packageListCriteria(projectId, repoName, option.packageName)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val records = separationPackageDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, 0, records)
    }

    override fun listVersionPage(
        projectId: String, repoName: String,
        packageKey: String, option: VersionListOption,
        separationDate: LocalDateTime,
    ): Page<PackageVersion> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        Preconditions.checkArgument(pageSize >= 0, "pageSize")
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val tPackage = separationPackageDao.findByKeyExcludeHistoryVersion(projectId, repoName, packageKey)
        return if (tPackage == null) {
            Pages.ofResponse(pageRequest, 0, emptyList())
        } else {
            val query = SeparationQueryHelper.versionListQuery(
                packageId = tPackage.id!!,
                name = option.version,
                separationDate = separationDate,
                sortProperty = option.sortProperty,
                direction = Sort.Direction.fromOptionalString(option.direction.toString()).orElse(Sort.Direction.DESC)
            )
            val records = separationPackageVersionDao.find(query.with(pageRequest)).map { convert(it)!! }
            Pages.ofResponse(pageRequest, 0, records)
        }
    }


    override fun countColdNodes(query: Query): Long {
        val separationDates = separationDatesFromQuery(query)
        if (separationDates.isEmpty()) return 0L
        val countQuery = Query.of(query).limit(0).skip(0)
        return separationDates.sumOf { date -> separationNodeDao.countByQuery(countQuery, date) }
    }

    override fun searchColdNodes(query: Query, skip: Long, limit: Int): List<MutableMap<String, Any?>> {
        if (limit <= 0) return emptyList()
        val separationDates = separationDatesFromQuery(query)
        if (separationDates.isEmpty()) return emptyList()
        val result = mutableListOf<MutableMap<String, Any?>>()
        var skipRemaining = skip
        var remaining = limit
        // 按降冷日期倒序遍历各分表，跨表实现正确偏移
        for (date in separationDates.sortedDescending()) {
            if (remaining <= 0) break
            val countQuery = Query.of(query).limit(0).skip(0)
            val count = separationNodeDao.countByQuery(countQuery, date)
            if (skipRemaining >= count) {
                skipRemaining -= count
                continue
            }
            val pageQuery = Query.of(query).skip(skipRemaining).limit(remaining)
            val rows = separationNodeDao.findByQuery(pageQuery, date)
            result.addAll(rows)
            remaining -= rows.size
            skipRemaining = 0
        }
        return result
    }

    /**
     * 从 MongoDB query 的顶层 document 中提取 projectId/repoName（简单等值条件），
     * 用于过滤出仅针对配置了降冷任务的仓库才查询冷数据。
     * 若 query 中不含顶层等值条件（如跨仓库复杂嵌套查询），则回退到全局所有降冷日期。
     */
    private fun separationDatesFromQuery(query: Query): Set<LocalDateTime> {
        val doc = query.queryObject
        val projectId = doc[TSeparationNode::projectId.name] as? String
        val repoName = doc[TSeparationNode::repoName.name] as? String
        return separationTaskService.findDistinctSeparationDate(projectId, repoName)
    }

    private fun packageListCriteria(projectId: String, repoName: String, packageName: String?): Query {
        return Query(where(TSeparationPackage::projectId).isEqualTo(projectId)
                         .and(TSeparationPackage::repoName).isEqualTo(repoName)
                         .apply {
                             packageName?.let { and(TSeparationPackage::name).regex("^$packageName", "i") }
                         })
    }

    private fun checkNodeListOption(option: NodeListOption) {
        Preconditions.checkArgument(
            option.sortProperty.none { !TSeparationNode::class.java.declaredFields.map { f -> f.name }.contains(it) },
            "sortProperty",
        )
        Preconditions.checkArgument(
            option.direction.none { it != Sort.Direction.DESC.name && it != Sort.Direction.ASC.name },
            "direction",
        )
    }

    companion object {
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
        const val PATH_IDX = "projectId_repoName_path_idx"


        fun toMap(metadataList: List<TMetadata>): Map<String, Any> {
            return metadataList?.associate { it.key to it.value }.orEmpty()
        }

        private fun convert(tPackageVersion: TSeparationPackageVersion?): PackageVersion? {
            return tPackageVersion?.let {
                PackageVersion(
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    name = it.name,
                    size = it.size,
                    downloads = it.downloads,
                    stageTag = it.stageTag,
                    metadata = toMap(it.metadata),
                    packageMetadata = it.metadata.map { m ->
                        MetadataModel(
                            key = m.key, value = m.value,
                            system = m.system, description = m.description,
                            link = m.link
                        )
                    },
                    tags = it.tags.orEmpty(),
                    extension = it.extension.orEmpty(),
                    contentPath = it.artifactPath,
                    manifestPath = it.manifestPath,
                    clusterNames = it.clusterNames
                )
            }
        }

        private fun convert(tPackage: TSeparationPackage?): PackageSummary? {
            return tPackage?.let {
                PackageSummary(
                    id = it.id.orEmpty(),
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    projectId = it.projectId,
                    repoName = it.repoName,
                    name = it.name,
                    key = it.key,
                    type = it.type,
                    latest = it.latest.orEmpty(),
                    downloads = it.downloads,
                    versions = it.versions,
                    description = it.description,
                    versionTag = it.versionTag.orEmpty(),
                    extension = it.extension.orEmpty(),
                    historyVersion = it.historyVersion
                )
            }
        }

        private fun convert(tNode: TSeparationNode?): NodeInfo? {
            return tNode?.let {
                val metadata = it.metadata?.let { it1 -> toMap(it1) }
                NodeInfo(
                    id = it.id,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    projectId = it.projectId,
                    repoName = it.repoName,
                    folder = it.folder,
                    path = it.path,
                    name = it.name,
                    fullPath = it.fullPath,
                    size = if (it.size < 0L) 0L else it.size,
                    nodeNum = it.nodeNum?.let { nodeNum ->
                        if (nodeNum < 0L) 0L else nodeNum
                    },
                    sha256 = it.sha256,
                    md5 = it.md5,
                    crc64ecma = it.crc64ecma,
                    metadata = metadata,
                    nodeMetadata = it.metadata?.map { m ->
                        MetadataModel(
                            key = m.key, value = m.value,
                            system = m.system, description = m.description,
                            link = m.link
                        )
                    },
                    copyFromCredentialsKey = it.copyFromCredentialsKey,
                    copyIntoCredentialsKey = it.copyIntoCredentialsKey,
                    deleted = it.deleted?.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastAccessDate = it.lastAccessDate?.format(DateTimeFormatter.ISO_DATE_TIME),
                    clusterNames = it.clusterNames,
                    archived = it.archived,
                    compressed = it.compressed,
                )
            }
        }
    }
}
