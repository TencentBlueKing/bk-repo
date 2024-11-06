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

package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.path.PathUtils.combinePath
import com.tencent.bkrepo.common.artifact.path.PathUtils.resolveAncestor
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.config.OpEmptyFolderStatJobProperties
import com.tencent.bkrepo.opdata.constant.OPDATA_PATH
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_ID
import com.tencent.bkrepo.opdata.constant.OPDATA_REPO_NAME
import com.tencent.bkrepo.opdata.job.pojo.EmptyFolderMetric
import com.tencent.bkrepo.opdata.job.pojo.JobContext
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class EmptyFolderStatJob(
    private val mongoTemplate: MongoTemplate,
    opJobProperties: OpEmptyFolderStatJobProperties
) : BaseJob<EmptyFolderMetric>(mongoTemplate, opJobProperties) {

    fun statFolderSize(projectId: String, repoName: String, path: String = StringPool.SLASH): List<EmptyFolderMetric> {
        logger.info("start to stat empty folder record under $path in repo $projectId|$repoName")
        val index = HashShardingUtils.shardingSequenceFor(projectId, 256)
        val extraMap = mutableMapOf(
            OPDATA_PROJECT_ID to projectId,
            OPDATA_REPO_NAME to repoName,
            OPDATA_PATH to path,
        )
        val context = JobContext<EmptyFolderMetric>(
            extraInfo = extraMap
        )
        stat(
            shardingIndex = index,
            context = context
        )
        return mergeResult(context.metrics, context.folderSets).values.toList()
    }

    fun deleteEmptyFolder(projectId: String, repoName: String, objectId: String) {
        val index = HashShardingUtils.shardingSequenceFor(projectId, 256)
        val collectionName = collectionName(index)
        val query = Query(
            Criteria.where(FIELD_NAME_ID).isEqualTo(ObjectId(objectId))
        )
        mongoTemplate.remove(query, Map::class.java, collectionName)
    }

    override fun statAction(
        startId: String,
        collectionName: String,
        context: JobContext<EmptyFolderMetric>
    ) {
        val query = Query(
            Criteria.where(FIELD_NAME_ID).gte(ObjectId(startId))
                .and(NodeDetail::projectId.name).`is`(context.extraInfo[OPDATA_PROJECT_ID])
                .and(NodeDetail::repoName.name).`is`(context.extraInfo[OPDATA_REPO_NAME])
        ).with(Sort.by(FIELD_NAME_ID))
            .limit(opJobProperties.batchSize)
        query.fields().include(
            NodeDetail::path.name, NodeDetail::name.name, FIELD_NAME_DELETED,
            NodeDetail::fullPath.name, NodeDetail::folder.name, FIELD_NAME_ID
        )
        val nodes = mongoTemplate.find(query, Map::class.java, collectionName)
        nodes.forEach {
            val deleted = it[FIELD_NAME_DELETED]
            if (deleted != null) return@forEach
            val name = it[NodeDetail::name.name].toString()
            val isFolder = it[NodeDetail::folder.name].toString().toBoolean()
            val path = it[NodeDetail::path.name].toString()
            val fullPath = it[NodeDetail::fullPath.name].toString()
            val objectId = it[FIELD_NAME_ID].toString()
            if (isFolder) {
                val tempPath = combinePath(path, name)
                if (!tempPath.startsWith(context.extraInfo[OPDATA_PATH]!!)) return@forEach
                context.metrics.getOrPut(tempPath) {
                    EmptyFolderMetric(tempPath, objectId)
                }
                return@forEach
            }
            if (!fullPath.startsWith(context.extraInfo[OPDATA_PATH]!!)) return@forEach
            resolveAncestor(fullPath).forEach { str ->
                if (str.startsWith(context.extraInfo[OPDATA_PATH]!!)) {
                    context.folderSets.add(str)
                }
            }
        }
    }

    private fun mergeResult(
        metrics: ConcurrentHashMap<String, EmptyFolderMetric>,
        folderSets: ConcurrentHashSet<String>
    ): ConcurrentHashMap<String, EmptyFolderMetric> {
        if (folderSets.isEmpty()) return metrics
        metrics.keys.forEach {
            if (folderSets.contains(it)) {
                metrics.remove(it)
            }
        }
        return metrics
    }

    override fun collectionName(shardingIndex: Int?): String = "${TABLE_PREFIX}$shardingIndex"

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val TABLE_PREFIX = "node_"
    }
}
