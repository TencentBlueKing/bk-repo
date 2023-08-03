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

package com.tencent.bkrepo.repository.listener

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.node.NodeMovedEvent
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.model.TFolderSizeStat
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.util.NodeQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.regex
import org.springframework.data.mongodb.core.query.where
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer


/**
 * 节点事件监听，用于folder size统计
 * 对应destination为对应ArtifactEvent.topic
 */
@Component("nodeModify")
class NodeModifyEventConsumer(
    private val mongoTemplate: MongoTemplate,
    private val nodeClient: NodeClient
    ) : Consumer<Message<ArtifactEvent>> {

    private val executors = run {
        val namedThreadFactory = ThreadFactoryBuilder().setNameFormat("nodeModify-worker-%d").build()
        ThreadPoolExecutor(
            1, 5, 30, TimeUnit.SECONDS,
            LinkedBlockingQueue(1024), namedThreadFactory, ThreadPoolExecutor.CallerRunsPolicy()
        )
    }

    /**
     * 允许接收的事件类型
     */
    private val acceptTypes = setOf(
        EventType.NODE_COPIED,
        EventType.NODE_CREATED,
        EventType.NODE_DELETED,
        EventType.NODE_MOVED
    )

    override fun accept(message: Message<ArtifactEvent>) {
        if (!acceptTypes.contains(message.payload.type)) {
            return
        }
        updateFolderSize(message.payload)
    }

    private fun updateFolderSize(event: ArtifactEvent) {
        // TODO 当第一次统计数据还没开始或者结束时如何处理
        when (event.type) {
            EventType.NODE_MOVED -> {
                val movedEvent = event as NodeMovedEvent
                decreaseFolderSize(
                    projectId = movedEvent.projectId,
                    repoName = movedEvent.repoName,
                    fullPath = movedEvent.resourceKey
                )
                addFolderSize(
                    projectId = event.dstProjectId,
                    repoName = event.dstRepoName,
                    fullPath = event.dstFullPath
                )
            }
            EventType.NODE_DELETED -> {
                decreaseFolderSize(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey
                )
            }
            EventType.NODE_COPIED -> {
                addFolderSize(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey
                )
            }
            EventType.NODE_CREATED -> {
                val size = event.data["size"].toString().toLongOrNull() ?:
                nodeClient.getNodeDetail(event.projectId, event.repoName, event.resourceKey).data!!.size
                addFolderSize(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    fullPath = event.resourceKey,
                    size = size
                )
            }
            else -> throw UnsupportedOperationException()
        }
    }



    private fun addFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long = 0
    ) {
        val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, fullPath).data ?: return

        PathUtils.resolveAncestor(fullPath).filter { it != PathUtils.ROOT }.forEach {
            val folderPath = it.removeSuffix(StringPool.SLASH)
            val query = Query(
                Criteria.where(TFolderSizeStat::projectId.name).isEqualTo(projectId)
                    .and(TFolderSizeStat::repoName.name).isEqualTo(repoName)
                    .and(TFolderSizeStat::folderPath.name).isEqualTo(folderPath)
            )
            val update = Update().inc(TFolderSizeStat::size.name, size)
                .setOnInsert(TFolderSizeStat::createdDate.name, LocalDateTime.now())
                .set(TFolderSizeStat::lastModifiedDate.name, LocalDateTime.now())

            val options = FindAndModifyOptions().upsert(true)
            mongoTemplate.findAndModify(query, update, options, TFolderSizeStat::class.java)
        }

        if (nodeDetail.folder) {
            addSubFolderSize(projectId, repoName, nodeDetail.fullPath)
        } else {
            val path = PathUtils.resolveParent(fullPath).removeSuffix(StringPool.SLASH)
            val query = Query(
                Criteria.where(TFolderSizeStat::projectId.name).isEqualTo(projectId)
                    .and(TFolderSizeStat::repoName.name).isEqualTo(repoName)
                    .and(TFolderSizeStat::folderPath.name).isEqualTo(path)
            )
            val update = Update().inc(TFolderSizeStat::size.name, size)
                .set(TFolderSizeStat::lastModifiedDate.name, LocalDateTime.now())
            val options = FindAndModifyOptions().upsert(true)
            mongoTemplate.findAndModify(query, update, options, TFolderSizeStat::class.java)
        }
    }


    private fun decreaseFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String
    ) {
        val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
                ?: nodeClient.getDeletedNodeDetail(projectId, repoName, fullPath).data!!.firstOrNull() ?: return
        if (nodeDetail.folder) {
            //TODO 实际先删除目录，再新增节点，消息消费时先新增节点，再删除目录会有问题
            val query = Query(
                Criteria.where(TFolderSizeStat::projectId.name).isEqualTo(projectId)
                    .and(TFolderSizeStat::repoName.name).isEqualTo(repoName)
                    .andOperator(TFolderSizeStat::folderPath.regex("^${EscapeUtils.escapeRegex(nodeDetail.fullPath)}", "i"))
            )
            mongoTemplate.find(query, TFolderSizeStat::class.java).forEach {
                val folderRealTimeSize = computeFolderSize(it.projectId, it.repoName, it.folderPath)
                val tempQuery = Query(
                    Criteria.where(TFolderSizeStat::projectId.name).isEqualTo(projectId)
                        .and(TFolderSizeStat::repoName.name).isEqualTo(repoName)
                        .and(TFolderSizeStat::folderPath.name).isEqualTo(it.folderPath)
                )
                if (folderRealTimeSize == null) {
                    mongoTemplate.remove(tempQuery, TFolderSizeStat::class.java)
                } else {
                    if (folderRealTimeSize != it.size) {
                        val update = Update().set(TFolderSizeStat::size.name, folderRealTimeSize)
                            .set(TFolderSizeStat::lastModifiedDate.name, LocalDateTime.now())
                        val options = FindAndModifyOptions().upsert(true)
                        mongoTemplate.findAndModify(tempQuery, update, options, TFolderSizeStat::class.java)
                    }
                }
            }
        } else {
            // 节点删除和创建的顺序对于统计目录大小是没有影响的
            val query = Query(
                Criteria.where(TFolderSizeStat::projectId.name).isEqualTo(projectId)
                    .and(TFolderSizeStat::repoName.name).isEqualTo(repoName)
                    .and(TFolderSizeStat::folderPath.name).isEqualTo(nodeDetail.path.removeSuffix(StringPool.SLASH))
            )
            val update = Update().inc(TFolderSizeStat::size.name, (0 - nodeDetail.size))
                .set(TFolderSizeStat::lastModifiedDate.name, LocalDateTime.now())
            mongoTemplate.findAndModify(query, update, TFolderSizeStat::class.java)
        }
    }

    private fun buildQuery(
        projectId: String,
        repoName: String,
        fullPath: String
    ): Query {
        return Query(
            Criteria.where(TFolderSizeStat::projectId.name).isEqualTo(projectId)
                .and(TFolderSizeStat::repoName.name).isEqualTo(repoName)
                .and(TFolderSizeStat::folderPath.name).isEqualTo(fullPath)
        )
    }


    private fun addSubFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String
    ) {
        var pageNumber = 1
        var flag = true
        do {
            val listOption = NodeListOption(
                includeFolder = true, deep = true, sort = true, pageSize = 200, pageNumber = pageNumber
            )
            val query = NodeQueryHelper.nodeListQuery(projectId, repoName, fullPath, listOption)
            //  遍历目录下的子目录
            val queryResult = mongoTemplate.find(query, TNode::class.java)
            if (queryResult.isEmpty()) flag = false
            queryResult.forEach {
                if (!it.folder) {
                    flag = false
                }
                computeFolderSize(it.projectId, it.repoName, it.fullPath)?.let { size ->
                    addFolderSize(projectId, repoName, it.fullPath, size)
                }
            }
            pageNumber++
        } while (flag)
    }


    private fun buildNodeQuery(projectId: String, repoName: String, fullPath: String): Criteria {
        return where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::path).isEqualTo(fullPath+StringPool.SLASH)
            .and(TNode::deleted).isEqualTo(null)
    }

    private fun aggregateComputeSize(criteria: Criteria, collectionName: String): Long? {
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(TNode::size.name).`as`(TNode::size.name)
        )
        val aggregateResult = mongoTemplate.aggregate(aggregation, collectionName, HashMap::class.java)
        return aggregateResult.mappedResults.firstOrNull()?.get(TNode::size.name) as? Long
    }

    private fun computeFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String
    ): Long? {
        val folderSizeQuery = buildNodeQuery(projectId, repoName, fullPath)
        val nodeCollectionName = COLLECTION_NODE_PREFIX + shardingSequence(projectId, SHARDING_COUNT)
        return aggregateComputeSize(folderSizeQuery, nodeCollectionName)
    }

    /**
     * 计算所在分表序号
     */
    fun shardingSequence(value: Any, shardingCount: Int): Int {
        val hashCode = value.hashCode()
        return hashCode and shardingCount - 1
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeModifyEventConsumer::class.java)
        private const val SHARDING_COUNT = 256
        private const val COLLECTION_NODE_PREFIX = "node_"

    }
}