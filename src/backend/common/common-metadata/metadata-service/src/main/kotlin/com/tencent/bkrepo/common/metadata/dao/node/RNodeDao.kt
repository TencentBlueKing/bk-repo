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

package com.tencent.bkrepo.common.metadata.dao.node

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.routing.NodeScatterQueryService
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.mongo.reactive.dao.HashShardingMongoReactiveDao
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.LocalDateTime


/**
 * 节点 Dao
 */
@Component
@Conditional(ReactiveCondition::class)
class RNodeDao(
    @Autowired(required = false) private val scatterQueryService: NodeScatterQueryService? = null,
) : HashShardingMongoReactiveDao<TNode>() {

    /**
     * 查询节点
     */
    suspend fun findNode(projectId: String, repoName: String, fullPath: String): TNode? {
        // 系统设计上不保存根目录节点到数据库，但是有用户会手动创建根目录节点
        return this.findOne(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))
            ?: if (PathUtils.isRoot(fullPath)) buildRootNode(projectId, repoName) else null
    }

    suspend fun findNodeIncludeDeleted(projectId: String, repoName: String, fullPath: String): List<TNode> {
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::fullPath).isEqualTo(fullPath)
        return find(Query(criteria))
    }

    suspend fun findById(projectId: String, id: String): TNode? {
        return findOne(Query(TNode::projectId.isEqualTo(projectId).and(ID).isEqualTo(id)))
    }

    suspend fun setNodeArchived(projectId: String, nodeId: String, archived: Boolean): UpdateResult {
        val query = Query(TNode::projectId.isEqualTo(projectId).and(ID).isEqualTo(nodeId))
        val update = Update().set(TNode::archived.name, archived)
            .set(TNode::lastModifiedDate.name, LocalDateTime.now())
        return updateFirst(query, update)
    }

    /**
     * 查询节点是否存在
     */
    suspend fun exists(projectId: String, repoName: String, fullPath: String): Boolean {
        if (PathUtils.isRoot(fullPath)) {
            return true
        }
        return this.exists(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))
    }

    suspend fun checkFolder(projectId: String, repoName: String, fullPath: String): Boolean {
        if (PathUtils.isRoot(fullPath)) {
            return true
        }
        return this.exists(NodeQueryHelper.nodeFolderQuery(projectId, repoName, fullPath))
    }

    /**
     * 更新目录下变更的文件数量以及涉及的文件大小
     */
    suspend fun incSizeAndNodeNumOfFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        nodeNum: Long
    ) {
        val query = NodeQueryHelper.nodeFolderQuery(projectId, repoName, fullPath)
        val update = Update()
            .inc(TNode::size.name, size)
            .inc(TNode::nodeNum.name, nodeNum)
            .set(TNode::lastModifiedDate.name, LocalDateTime.now())
        val options = FindAndModifyOptions()
        options.returnNew(true)
        val tNode = this.findAndModify(query, update, options, TNode::class.java)
        if (tNode != null && (tNode.nodeNum!! < 0L || tNode.size < 0L )) {
            // 如果数据为负数，将其设置为 0
            val updateMax = Update()
                .max(TNode::size.name, 0L)
                .max(TNode::nodeNum.name, 0L)
            this.updateFirst(query, updateMax)
        }
    }

    /**
     * 设置目录下的文件数量以及文件大小
     */
    suspend fun setSizeAndNodeNumOfFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        nodeNum: Long
    ) {
        val query = NodeQueryHelper.nodeFolderQuery(projectId, repoName, fullPath)
        val update = Update().set(TNode::size.name, size)
            .set(TNode::nodeNum.name, nodeNum)
            .set(TNode::lastModifiedDate.name, LocalDateTime.now())
        this.updateFirst(query, update)
    }

    override suspend fun updateFirst(query: Query, update: Update) =
        super.updateFirst(query, NodeQueryHelper.touchLastModified(update))

    override suspend fun updateMulti(query: Query, update: Update) =
        super.updateMulti(query, NodeQueryHelper.touchLastModified(update))

    override suspend fun <T> findAndModify(
        query: Query,
        update: Update,
        options: FindAndModifyOptions,
        clazz: Class<T>,
    ): T? = super.findAndModify(query, NodeQueryHelper.touchLastModified(update), options, clazz)


    /**
     * 根据[sha256]分页查询节点，需要遍历所有分表
     *
     * @param includeDeleted 是否包含被删除的节点
     */
    suspend fun pageBySha256(
        sha256: String,
        option: NodeListOption,
        includeDeleted: Boolean = false
    ): Page<TNode> {
        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)

        // 构造查询条件
        val criteria = where(TNode::sha256).isEqualTo(sha256).and(TNode::folder).isEqualTo(false)
        if (!includeDeleted) {
            criteria.and(TNode::deleted).isEqualTo(null)
        }
        val query = Query(criteria)
        if (!option.includeMetadata) {
            query.fields().exclude(TNode::metadata.name)
        }

        val scatterService = scatterQueryService
        if (scatterService?.isScatterReadEnabled() == true) {
            if (pageRequest.offset > MAX_SCATTER_OFFSET) {
                throw BadRequestException(
                    CommonMessageCode.PARAMETER_INVALID,
                    "Scatter query does not support deep pagination (offset > $MAX_SCATTER_OFFSET)",
                )
            }
            query.limit((pageRequest.offset + pageRequest.pageSize).toInt().coerceAtMost(MAX_SCATTER_FETCH))
            val all = withContext(Dispatchers.IO) {
                scatterService.scatterFind(query, TNode::class.java, allCollectionNames())
            }
            val offset = pageRequest.offset.toInt().coerceAtMost(all.size)
            val end = (offset + pageRequest.pageSize).coerceAtMost(all.size)
            return PageImpl(all.subList(offset, end), pageRequest, all.size.toLong())
        }
        return pageWithoutShardingKey(pageRequest, query)
    }

    /**
     * 根据[sha256]查询node列表，用于不需要分页的场景提高查询速度
     *
     * @param sha256 待查询sha256
     * @param limit 查询选项
     * @param includeMetadata 是否包含元数据
     * @param includeDeleted 是否包含被删除的制品
     * @param tillLimit 为true时将遍历所有分表直到查询到的结果数量达到limit
     *
     * @return 指定sha256的node列表
     */
    suspend fun listBySha256(
        sha256: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        includeMetadata: Boolean = false,
        includeDeleted: Boolean = true,
        tillLimit: Boolean = true,
    ): List<TNode> {
        // 构造查询条件
        val criteria = where(TNode::sha256).isEqualTo(sha256).and(TNode::folder).isEqualTo(false)
        if (!includeDeleted) {
            criteria.and(TNode::deleted).isEqualTo(null)
        }
        val query = Query(criteria)
        if (!includeMetadata) {
            query.fields().exclude(TNode::metadata.name)
        }

        val scatterService = scatterQueryService
        if (scatterService?.isScatterReadEnabled() == true) {
            query.limit(limit)
            return withContext(Dispatchers.IO) {
                scatterService.scatterFind(query, TNode::class.java, allCollectionNames())
            }.take(limit)
        }

        if (shardingCount <= 0 || shardingCount > MAX_SHARDING_COUNT_OF_PAGE_QUERY) {
            throw UnsupportedOperationException()
        }

        // 遍历所有分表进行查询
        val template = determineReactiveMongoOperations()
        val result = ArrayList<TNode>()
        for (sequence in 0 until shardingCount) {
            query.limit(limit - result.size)
            val collectionName = parseSequenceToCollectionName(sequence)
            result.addAll(template.find(query, classType, collectionName).collectList().awaitSingle())
            if (result.isNotEmpty() && !tillLimit || result.size == limit) {
                break
            }
        }

        return result
    }

    private fun allCollectionNames(): List<String> =
        (0 until shardingCount).map { parseSequenceToCollectionName(it) }

    companion object {
        private const val MAX_SCATTER_OFFSET = 10_000L
        private const val MAX_SCATTER_FETCH = 10_000
        private const val MAX_SHARDING_COUNT_OF_PAGE_QUERY = 256

        fun buildRootNode(projectId: String, repoName: String): TNode {
            return TNode(
                createdBy = StringPool.EMPTY,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = StringPool.EMPTY,
                lastModifiedDate = LocalDateTime.now(),
                projectId = projectId,
                repoName = repoName,
                folder = true,
                path = PathUtils.ROOT,
                name = StringPool.EMPTY,
                fullPath = PathUtils.ROOT,
                size = 0
            )
        }
    }
}