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

package com.tencent.bkrepo.opdata.model

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class ArchiveInfoModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
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
    fun refresh() {
        archiveInfo = stat()
    }

    private fun stat(): Map<String, Array<Long>> {
        logger.info("Start update archive metrics.")
        // 遍历节点表
        val criteria = Criteria.where("archived").isEqualTo(true)
        val query = Query(criteria)
        val statistics = ConcurrentHashMap<String, Array<AtomicLong>>()
        GcInfoModel.forEachCollectionAsync {
            val nodes = mongoTemplate.find<Node>(query, it)
            for (node in nodes) {
                val repo = "${node.projectId}/${node.repoName}"
                // array info: [archiveNum,archiveSize]
                val longs = statistics.getOrPut(repo) { arrayOf(AtomicLong(), AtomicLong()) }
                longs[0].incrementAndGet()
                longs[1].addAndGet(node.size)
            }
        }
        statistics[SUM] = GcInfoModel.reduce(statistics)
        logger.info("Update archive metrics successful.")
        return statistics.mapValues { arrayOf(it.value[0].get(), it.value[1].get()) }
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
