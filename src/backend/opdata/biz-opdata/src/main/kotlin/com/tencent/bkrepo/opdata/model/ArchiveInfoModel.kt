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

import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.opdata.config.OpArchiveOrGcProperties
import com.tencent.bkrepo.opdata.model.GcInfoModel.Companion.BATCH_SIZE
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.stream
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong


@Service
class ArchiveInfoModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val opArchiveOrGcProperties: OpArchiveOrGcProperties,
) {

    private var archiveInfo: Map<String, Array<Long>> = emptyMap()

    /**
     * <repo,[archiveNum,archiveSize]>
     * */
    fun info(): Map<String, Array<Long>> {
        if (archiveInfo.isEmpty()) {
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
        if (!opArchiveOrGcProperties.archiveEnabled) return emptyMap()
        logger.info("Start update archive metrics.")
        val statistics = ConcurrentHashMap<String, Array<AtomicLong>>()
        val criteria = Criteria.where("archived").isEqualTo(true)
            .and("deleted").isEqualTo(null)

        if (opArchiveOrGcProperties.archiveProjects.isEmpty()) {
            // 处理所有项目的归档数据
            val query = Query(criteria).cursorBatchSize(BATCH_SIZE)
            // 遍历节点表
            GcInfoModel.forEachCollectionAsync { collection ->
                mongoTemplate.stream<Node>(query, collection).use { nodes ->
                    nodes.forEach { node ->
                        updateStatistics(statistics, node)
                    }
                }
            }
        } else {
            // 处理指定项目的归档数据
            opArchiveOrGcProperties.archiveProjects.forEach { project ->
                val collectionName = "node_${HashShardingUtils.shardingSequenceFor(project, SHARDING_COUNT)}"
                val query = Query(Criteria.where("projectId").isEqualTo(project).andOperator(criteria))
                    .cursorBatchSize(BATCH_SIZE)
                mongoTemplate.stream<Node>(query, collectionName).use { nodes ->
                    nodes.forEach { node ->
                        updateStatistics(statistics, node)
                    }
                }
            }
        }

        statistics[SUM] = GcInfoModel.reduce(statistics)
        logger.info("Update archive metrics successful.")
        return statistics.mapValues { arrayOf(it.value[0].get(), it.value[1].get()) }
    }

    private fun updateStatistics(statistics: ConcurrentHashMap<String, Array<AtomicLong>>, node: Node) {
        val repo = "${node.projectId}/${node.repoName}"
        // 数组信息: [归档文件数量, 归档文件总大小]
        val counts = statistics.getOrPut(repo) { arrayOf(AtomicLong(), AtomicLong()) }
        counts[0].incrementAndGet()
        counts[1].addAndGet(node.size)
    }

    data class Node(
        val projectId: String,
        val repoName: String,
        val sha256: String,
        val size: Long,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveInfoModel::class.java)
        private const val SUM = "SUM"
    }
}
