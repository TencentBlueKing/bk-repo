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

package com.tencent.bkrepo.job.batch.task.other

import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeCopyJobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.NodeCopyJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.repository.constant.DEFAULT_STORAGE_CREDENTIALS_KEY
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
@EnableConfigurationProperties(NodeCopyJobProperties::class)
class NodeCopyJob(
    private val storageService: StorageService,
    private val fileReferenceService: FileReferenceService,
    properties: NodeCopyJobProperties
) : MongoDbBatchJob<NodeCopyJob.NodeCopyData, NodeCopyJobContext>(properties) {

    override fun start(): Boolean {
        return super.start()
    }

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT)
            .map { "$COLLECTION_NAME_PREFIX$it" }
            .toList()
    }

    override fun buildQuery(): Query {
        return Query(where(NodeCopyData::copyFromCredentialsKey).ne(null))
    }

    override fun mapToEntity(row: Map<String, Any?>): NodeCopyData {
        return NodeCopyData(row)
    }

    override fun entityClass(): KClass<NodeCopyData> = NodeCopyData::class

    override fun createJobContext(): NodeCopyJobContext = NodeCopyJobContext()

    override fun run(row: NodeCopyData, collectionName: String, context: NodeCopyJobContext) {
        var digest: String? = null
        var srcCredentials: StorageCredentials? = null
        var dstCredentials: StorageCredentials? = null
        try {
            digest = row.sha256
            srcCredentials = RepositoryCommonUtils.getStorageCredentials(row.copyFromCredentialsKey)
            val repositoryDetail = RepositoryCommonUtils.getRepositoryDetail(row.projectId, row.repoName)
            dstCredentials = repositoryDetail.storageCredentials
            fileReferenceCheck(dstCredentials, row, digest)
            val targetCopy = TargetCopy(targetCredentialsKey = dstCredentials?.key, digest = digest)
            if (context.alreadyCopySet.contains(targetCopy)) {
                afterCopySuccess(row, collectionName)
                return
            }
            if (storageService.exist(digest, srcCredentials)) {
                safeCopy(digest, srcCredentials, dstCredentials)
                afterCopySuccess(row, collectionName)
                context.alreadyCopySet.add(targetCopy)
            } else {
                context.fileMissing.incrementAndGet()
                logger.warn("File[$digest] is missing on [$srcCredentials], skip copy.")
            }
        } catch (e: Exception) {
            throw JobExecuteException("Failed to copy file[$digest] from [$srcCredentials] to [$dstCredentials].", e)
        }
    }

    /**
     * 防止并发拷贝相同文件报错
     * */
    private fun safeCopy(
        digest: String,
        srcCredentials: StorageCredentials?,
        dstCredentials: StorageCredentials?
    ) {
        val key = "$digest-${srcCredentials?.key}-${dstCredentials?.key}"
        synchronized(key.intern()) {
            storageService.copy(digest, srcCredentials, dstCredentials)
        }
    }

    class NodeCopyData(map: Map<String, Any?>) {
        val id: String? by map
        val sha256: String by map
        val projectId: String by map
        val repoName: String by map
        val copyFromCredentialsKey: String by map
        val copyIntoCredentialsKey: String by map
    }

    /**
     * 文件引用核对
     * 拷贝时的存储实例与当前仓库的存储实例不同，说明仓库已经迁移到其他存储实例，
     * 则原先增加引用的存储实例，文件引用要减1还原
     * 当前存储实例引用加1
     * */
    private fun fileReferenceCheck(
        dstCredentials: StorageCredentials?,
        node: NodeCopyData,
        digest: String
    ) {
        var dstCredentialsKey: String? = dstCredentials?.key ?: DEFAULT_STORAGE_CREDENTIALS_KEY
        if (dstCredentialsKey != node.copyIntoCredentialsKey) {
            fileReferenceService.decrement(digest, node.copyIntoCredentialsKey)
            if (dstCredentialsKey == DEFAULT_STORAGE_CREDENTIALS_KEY) {
                // 还原为默认存储key为null
                dstCredentialsKey = null
            }
            fileReferenceService.increment(digest, dstCredentialsKey)
        }
    }

    /**
     * 拷贝成功后，删除存储映射
     * */
    private fun afterCopySuccess(node: NodeCopyData, collectionName: String) {
        with(node) {
            val update = Update().set(node::copyFromCredentialsKey.name, null)
                .set(node::copyIntoCredentialsKey.name, null)
            mongoTemplate.updateFirst(Query(Criteria(ID).isEqualTo(id)), update, collectionName)
        }
    }

    data class TargetCopy(
        val targetCredentialsKey: String?,
        val digest: String
    )

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME_PREFIX = "node_"
    }
}
