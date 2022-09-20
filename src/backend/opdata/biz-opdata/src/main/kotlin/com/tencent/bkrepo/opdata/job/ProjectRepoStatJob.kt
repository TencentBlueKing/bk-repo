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

package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.config.OpProjectRepoStatJobProperties
import com.tencent.bkrepo.opdata.job.pojo.ProjectMetrics
import com.tencent.bkrepo.opdata.model.TFolderMetrics
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.RepoMetrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * stat bkrepo running status
 */
@Component
class ProjectRepoStatJob(
    private val projectMetricsRepository: ProjectMetricsRepository,
    private val mongoTemplate: MongoTemplate,
    opJobProperties: OpProjectRepoStatJobProperties
) : BaseJob<ProjectMetrics>(mongoTemplate, opJobProperties) {

    @Scheduled(cron = "00 00 17 * * ?")
    @SchedulerLock(name = "ProjectRepoStatJob", lockAtMostFor = "PT10H")
    fun statProjectRepoSize() {
        if (!opJobProperties.enabled) {
            logger.info("stat project repo size job was disabled")
            return
        }
        logger.info("start to stat project metrics")
        val projectMetricsList = convert(stat(runConcurrency = false))

        // 数据写入mongodb统计表
        projectMetricsRepository.deleteAll()
        logger.info("start to insert  mongodb metrics ")
        projectMetricsRepository.insert(projectMetricsList)
        logger.info("stat project metrics done")
    }

    private fun convert(projectMetricsList: List<ProjectMetrics>): List<TProjectMetrics> {
        val tProjectMetricsList = ArrayList<TProjectMetrics>(projectMetricsList.size)
        for (projectMetrics in projectMetricsList) {
            val projectNodeNum = projectMetrics.nodeNum.toLong()
            val projectCapSize = projectMetrics.capSize.toLong()
            if (projectNodeNum == 0L || projectCapSize == 0L) {
                // 只统计有效项目数据
                continue
            }
            val repoMetrics = ArrayList<RepoMetrics>(projectMetrics.repoMetrics.size)
            projectMetrics.repoMetrics.values.forEach { repo ->
                val num = repo.num.toLong()
                val size = repo.size.toLong()
                // 有效仓库的统计数据
                if (num != 0L && size != 0L) {
                    logger.info("project : [${projectMetrics.projectId}],repo: [${repo.repoName}],size:[$repo]")
                    repoMetrics.add(RepoMetrics(repo.repoName, repo.credentialsKey, size / TOGIGABYTE, num))
                }
            }
            tProjectMetricsList.add(
                TProjectMetrics(
                    projectMetrics.projectId, projectNodeNum, projectCapSize / TOGIGABYTE, repoMetrics
                )
            )
        }
        return tProjectMetricsList
    }

    override fun statAction(
        startId: String,
        collectionName: String,
        metrics: ConcurrentHashMap<String, ProjectMetrics>,
        extraInfo: Map<String, String>,
        folderSets: ConcurrentHashSet<String>
    ) {
        val query = Query(Criteria.where(FIELD_NAME_ID).gte(ObjectId(startId)))
            .with(Sort.by(FIELD_NAME_ID))
            .limit(opJobProperties.batchSize)
        query.fields().include(
            TFolderMetrics::projectId.name, TFolderMetrics::repoName.name, TFolderMetrics::capSize.name,
            TFolderMetrics::nodeNum.name, TFolderMetrics::credentialsKey.name
        )
        val folders = mongoTemplate.find(query, Map::class.java, collectionName)
        folders.forEach {
            val projectId = it[TFolderMetrics::projectId.name].toString()
            val repoName = it[TFolderMetrics::repoName.name].toString()
            val size = it[TFolderMetrics::capSize.name].toString().toLong()
            val num = it[TFolderMetrics::nodeNum.name].toString().toLong()
            val credentialsKey = it[TFolderMetrics::credentialsKey.name].toString()
            metrics
                .getOrPut(projectId) { ProjectMetrics(projectId) }
                .apply {
                    capSize.add(size)
                    nodeNum.add(num)
                    val repo = repoMetrics.getOrPut(repoName) {
                        com.tencent.bkrepo.opdata.job.pojo.RepoMetrics(repoName, credentialsKey)
                    }
                    repo.size.add(size)
                    repo.num.add(num)
                }
        }
    }

    override fun collectionName(shardingIndex: Int ?): String = TABLE_NAME

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val TABLE_NAME = "folder_metrics"
        private const val TOGIGABYTE = 1024 * 1024 * 1024
    }
}
