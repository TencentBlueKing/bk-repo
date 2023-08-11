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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.util.EasyExcelUtils
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.opdata.constant.OPDATA_FOLDER_PATH
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_ID
import com.tencent.bkrepo.opdata.constant.OPDATA_REPO_NAME
import com.tencent.bkrepo.opdata.job.EmptyFolderStatJob
import com.tencent.bkrepo.opdata.job.pojo.EmptyFolderMetric
import com.tencent.bkrepo.opdata.model.StatInfo
import com.tencent.bkrepo.opdata.pojo.node.FolderInfo
import com.tencent.bkrepo.opdata.pojo.node.ListOption
import com.tencent.bkrepo.opdata.repository.FolderMetricsRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service

@Service
class NodeService(
    private val emptyFolderService: EmptyFolderStatJob,
    private val folderMetricsRepository: FolderMetricsRepository,
    private val mongoTemplate: MongoTemplate
    ) {

    fun getEmptyFolder(
        projectId: String,
        repoName: String,
        parentFolder: String = StringPool.SLASH
    ): List<EmptyFolderMetric> {
        return emptyFolderService.statFolderSize(projectId, repoName, parentFolder)
    }

    fun deleteEmptyFolder(projectId: String, repoName: String, parentFolder: String = StringPool.SLASH) {
        val emptyFolders = emptyFolderService.statFolderSize(projectId, repoName, parentFolder)
        emptyFolders.forEach {
            emptyFolderService.deleteEmptyFolder(projectId, repoName, it.objectId)
        }
    }

    fun getFirstLevelFolders(
        projectId: String,
        repoName: String,
        option: ListOption
    ): Page<FolderInfo> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val queryResult = folderMetricsRepository.findByProjectIdAndRepoNameOrderByCapSizeDesc(
                pageable = pageRequest, projectId = projectId, repoName = repoName
            ).map {
                FolderInfo(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    path = it.folderPath,
                    nodeNum = it.nodeNum,
                    capSize = it.capSize
                )
            }
            return Pages.ofResponse(pageRequest, queryResult.totalElements, queryResult.content)
        }
    }

    fun getFolderStatInfo(
        projectId: String,
        repoName: String,
        folderPath: String,
        option: ListOption
    ): List<FolderInfo> {
        return computeFolderSize(
            projectId = projectId,
            repoName = repoName,
            fullPath = folderPath,
        ).map {
            FolderInfo(
                projectId = projectId,
                repoName = repoName,
                path = it.key,
                capSize = it.value.first,
                nodeNum = it.value.second
            )
        }.sortedWith(compareBy(FolderInfo::path, FolderInfo::capSize))
    }

    fun downloadFolderStatInfo(
        projectId: String,
        repoName: String
    ) {
        val data = computeFolderSize(
            projectId = projectId,
            repoName = repoName,
            fullPath = PathUtils.ROOT,
        ).map {
            StatInfo(
                folderPath = it.key,
                size = it.value.first,
                nodeNum = it.value.second
            )
        }.sortedWith(compareBy(StatInfo::folderPath, StatInfo::size))
        EasyExcelUtils.download(
            data = data,
            name = "$projectId-$repoName",
            dataClass = StatInfo::class.java
        )
    }


    private fun computeFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String,
        deep: Boolean = false
    ): Map<String, Pair<Long, Long>> {
        val folderSizeQuery = buildNodeQuery(projectId, repoName, fullPath)
        val nodeCollectionName = COLLECTION_FOLDER_SIZE_STAT_PREFIX + shardingSequence(projectId, 256)
        val records = mongoTemplate.find(Query(folderSizeQuery), StatInfo::class.java, nodeCollectionName)
        val path = if (deep) {
            null
        } else {
            fullPath
        }
        return getTotalSizeByDirectory(records, path)
    }

    fun getStatInfo(directory: StatInfo, directories: List<StatInfo>): Pair<Long, Long> {
        var totalSize = directory.size
        var nodeNum = directory.nodeNum
        for (child in directories) {
            if (child.path == directory.folderPath) {
                val stat = getStatInfo(child, directories)
                totalSize += stat.first
                nodeNum += stat.second
            }
        }
        return Pair(totalSize, nodeNum)
    }

    private  fun getTotalSizeByDirectory(
        directories: List<StatInfo>,
        parentPath: String? = null
    ): Map<String, Pair<Long, Long>> {
        val result: MutableMap<String, Pair<Long, Long>> = HashMap()
        for (directory in directories) {
            if (parentPath.isNullOrEmpty()) {
                val stat = getStatInfo(directory, directories)
                result[directory.folderPath] = Pair(stat.first, stat.second)
            } else {
                if (directory.path == parentPath || directory.folderPath == parentPath) {
                    val stat = getStatInfo(directory, directories)
                    result[directory.folderPath] = Pair(stat.first, stat.second)
                }
            }
        }
        return result
    }

    private fun buildNodeQuery(projectId: String, repoName: String, path: String): Criteria {
        return Criteria.where(OPDATA_PROJECT_ID).isEqualTo(projectId)
            .and(OPDATA_REPO_NAME).isEqualTo(repoName)
            .and(OPDATA_FOLDER_PATH).regex("^${PathUtils.escapeRegex(path)}")
    }

    /**
     * 计算所在分表序号
     */
    private fun shardingSequence(value: Any, shardingCount: Int): Int {
        val hashCode = value.hashCode()
        return hashCode and shardingCount - 1
    }

    companion object {
        private const val COLLECTION_FOLDER_SIZE_STAT_PREFIX = "folder_stat_"

    }
}
