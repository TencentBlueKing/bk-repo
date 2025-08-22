/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.opdata.config.OpArchiveOrGcProperties
import com.tencent.bkrepo.opdata.model.GcInfoModel.Companion.BATCH_SIZE
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder


@Service
class ArchiveInfoModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val opArchiveOrGcProperties: OpArchiveOrGcProperties,
) {

    private var archiveInfo: Map<String, Array<Long>> = emptyMap()

    @Volatile
    private var refreshing = false

    /**
     * <repo,[archiveNum,archiveSize]>
     * */
    fun info(): Map<String, Array<Long>> {
        if (archiveInfo.isEmpty() && !refreshing) {
            archiveInfo = stat()
        }
        return archiveInfo
    }

    @Scheduled(cron = "0 0 4 * * ?")
    @SchedulerLock(name = "ArchiveInfoStatJob", lockAtMostFor = "PT24H")
    fun refresh() {
        archiveInfo = stat()
    }

    private fun stat(): Map<String, Array<Long>> {
        refreshing = true
        if (!opArchiveOrGcProperties.archiveEnabled) return emptyMap()
        logger.info("Start update archive metrics.")
        val statistics = ConcurrentHashMap<String, Array<AtomicLong>>()

        if (opArchiveOrGcProperties.archiveProjects.isEmpty()) {
            processAllProjects(statistics)
        } else {
            processSpecificProjects(statistics)
        }

        statistics[SUM] = GcInfoModel.reduce(statistics)
        logger.info("Update archive metrics successful.")
        refreshing = false
        return statistics.mapValues { arrayOf(it.value[0].get(), it.value[1].get()) }
    }

    private fun processAllProjects(statistics: ConcurrentHashMap<String, Array<AtomicLong>>) {
        // 处理所有项目的归档数据
        val query = Query().cursorBatchSize(BATCH_SIZE)
        // 遍历节点表
        processProject(query, statistics)
    }

    private fun processSpecificProjects(statistics: ConcurrentHashMap<String, Array<AtomicLong>>) {
        // 处理指定项目的归档数据
        opArchiveOrGcProperties.archiveProjects.forEach { project ->
            val query = Query(Criteria.where("name").isEqualTo(project))
            processProject(query, statistics)
        }
    }

    private fun processProject(query: Query, statistics: ConcurrentHashMap<String, Array<AtomicLong>>) {
        mongoTemplate.find(query, Project::class.java, "project").forEach { project ->
            val projectArchiveStatInfo = project.metadata.find { it.key == "archive" } ?: return
            updateStatistics(
                statistics, project.name, projectArchiveStatInfo.value as ConcurrentHashMap<String, RepoArchiveInfo>
            )
        }
    }

    private fun updateStatistics(
        statistics: ConcurrentHashMap<String, Array<AtomicLong>>,
        projectId: String,
        projectArchiveInfo: ConcurrentHashMap<String, RepoArchiveInfo>
    ) {
        projectArchiveInfo.forEach { (repoName, repo) ->
            val repoStr = "$projectId/$repoName"
            // 数组信息: [归档文件数量, 归档文件总大小]
            val counts = statistics.getOrPut(repoStr) { arrayOf(AtomicLong(), AtomicLong()) }
            counts[0].addAndGet(repo.num.toLong())
            counts[1].addAndGet(repo.size.toLong())
        }

    }

    data class Project(val name: String, val displayName: String, val metadata: List<ProjectMetadata> = emptyList())

    data class RepoArchiveInfo(
        val repoName: String,
        var size: LongAdder = LongAdder(),
        var num: LongAdder = LongAdder(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveInfoModel::class.java)
        private const val SUM = "SUM"
    }
}
