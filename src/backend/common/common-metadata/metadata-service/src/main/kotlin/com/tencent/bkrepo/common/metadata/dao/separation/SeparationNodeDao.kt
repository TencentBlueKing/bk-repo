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

package com.tencent.bkrepo.common.metadata.dao.separation

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.util.SeparationQueryHelper
import com.tencent.bkrepo.common.mongo.dao.sharding.MonthRangeShardingMongoDao
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SeparationNodeDao : MonthRangeShardingMongoDao<TSeparationNode>() {

    fun findOneByFullPath(
        projectId: String, repoName: String,
        fullPath: String, separationDate: LocalDateTime
    ): TSeparationNode? {
        return this.findOne(
            SeparationQueryHelper.fullPathQuery(
                projectId, repoName, fullPath, separationDate
            )
        )
    }

    fun findOne(
        projectId: String, repoName: String,
        versionPath: String, separationDate: LocalDateTime
    ): TSeparationNode? {
        return this.findOne(SeparationQueryHelper.pathQuery(projectId, repoName, versionPath, separationDate))
    }

    fun findById(id: String, separationDate: LocalDateTime): TSeparationNode? {
        return this.findOne(SeparationQueryHelper.nodeIdQuery(id, separationDate))

    }

    fun removeById(id: String, separationDate: LocalDateTime): DeleteResult {
        return this.remove(SeparationQueryHelper.nodeIdRemoveQuery(id, separationDate))
    }

    /**
     * 直接按集合名查询，用于不含 separationDate 分片条件的通用查询（如搜索场景）
     */
    fun findByQuery(query: Query, separationDate: LocalDateTime): List<MutableMap<String, Any?>> {
        val sequence = separationDate.year * 100 + separationDate.monthValue
        val collectionName = parseSequenceToCollectionName(sequence)
        return determineMongoTemplate().find(query, MutableMap::class.java, collectionName)
            as List<MutableMap<String, Any?>>
    }

    fun countByQuery(query: Query, separationDate: LocalDateTime): Long {
        val sequence = separationDate.year * 100 + separationDate.monthValue
        val collectionName = parseSequenceToCollectionName(sequence)
        return determineMongoTemplate().count(query, collectionName)
    }

    fun aggregateSizeByQuery(criteria: Criteria, separationDate: LocalDateTime): Long {
        val sequence = separationDate.year * 100 + separationDate.monthValue
        val collectionName = parseSequenceToCollectionName(sequence)
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(TSeparationNode::size.name).`as`("totalSize")
        )
        val result = determineMongoTemplate().aggregate(aggregation, collectionName, HashMap::class.java)
        return result.mappedResults.firstOrNull()?.get("totalSize") as? Long ?: 0L
    }
}
