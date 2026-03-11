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

package com.tencent.bkrepo.job.batch.task.other

import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils.parseMongoDateTimeStr
import com.tencent.bkrepo.job.config.properties.NodeCopyJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component

@Component
class NodeCopyJob(
    private val storageService: StorageService,
    private val blockNodeService: BlockNodeService,
    properties: NodeCopyJobProperties
) : DefaultContextMongoDbJob<NodeCopyJob.NodeCopyData>(properties) {

    override fun start(): Boolean {
        return super.start()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(3)

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

    override fun run(row: NodeCopyData, collectionName: String, context: JobContext) {
        var digest: String? = null
        var srcCredentials: StorageCredentials? = null
        var dstCredentials: StorageCredentials? = null
        try {
            digest = row.sha256
            srcCredentials = RepositoryCommonUtils.getStorageCredentials(row.copyFromCredentialsKey)
            dstCredentials = RepositoryCommonUtils.getRepositoryDetail(row.projectId, row.repoName).storageCredentials
            if (digest == FAKE_SHA256) {
                val nodeCreateDateTimeStr = row.createdDate.format(DateTimeFormatter.ISO_DATE_TIME)
                blockNodeService
                    .listAllBlocks(row.projectId, row.repoName, row.fullPath, nodeCreateDateTimeStr)
                    .forEach { safeCopy(it.sha256, srcCredentials, dstCredentials) }
            } else {
                safeCopy(digest, srcCredentials, dstCredentials)
            }
            afterCopySuccess(row, collectionName)
        } catch (e: Exception) {
            throw JobExecuteException(
                "Failed to copy file[${row.projectId}/${row.repoName}/${row.fullPath}] sha256[$digest] " +
                        "from [${srcCredentials?.key}] to [${dstCredentials?.key}].", e
            )
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
        val fullPath: String by map
        val createdDate: LocalDateTime = parseMongoDateTimeStr(map[TNode::createdDate.name].toString())!!
        val copyFromCredentialsKey: String by map
        val copyIntoCredentialsKey: String by map
    }

    /**
     * 拷贝成功后，删除存储映射
     * */
    private fun afterCopySuccess(node: NodeCopyData, collectionName: String) {
        with(node) {
            val update = Update().set(node::copyFromCredentialsKey.name, null)
                .set(node::copyIntoCredentialsKey.name, null)
            mongoTemplate.updateFirst(Query(Criteria(ID).isEqualTo(id)), update, collectionName)
            with(node) {
                logger.info("copy node[$projectId/$repoName/$fullPath success, digest[$sha256]]")
            }
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME_PREFIX = "node_"
    }
}
