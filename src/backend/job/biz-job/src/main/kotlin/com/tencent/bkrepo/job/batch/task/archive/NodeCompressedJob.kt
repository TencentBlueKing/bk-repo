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

package com.tencent.bkrepo.job.batch.task.archive

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.NodeCompressedJobProperties
import com.tencent.bkrepo.repository.pojo.node.service.NodeCompressedRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass

/**
 * 节点压缩任务
 *
 * 将已压缩的node打赏compressed标签
 * 1. 找到所有已压缩的节点
 * 2. 设置compressed为true
 * 3. 删除原文件
 * 4. 完成压缩
 * */
@Component
@EnableConfigurationProperties(NodeCompressedJobProperties::class)
class NodeCompressedJob(
    properties: NodeCompressedJobProperties,
    val nodeService: NodeService,
    val archiveClient: ArchiveClient,
    val storageService: StorageService,
) : MongoDbBatchJob<NodeCompressedJob.CompressFile, NodeContext>(properties) {
    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf("compress_file")
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where("status").isEqualTo(CompressStatus.COMPRESSED))
    }

    override fun run(row: CompressFile, collectionName: String, context: NodeContext) {
        with(row) {
            val storageCredentials = storageCredentialsKey?.let {
                RepositoryCommonUtils.getStorageCredentials(storageCredentialsKey)
            }
            val bdFileName = "$sha256.bd"
            if (!storageService.exist(bdFileName, storageCredentials)) {
                logger.warn("Miss file $bdFileName.")
                return
            }
            listNode(sha256, storageCredentialsKey).forEach {
                val compressedRequest = NodeCompressedRequest(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    operator = lastModifiedBy,
                )
                nodeService.compressedNode(compressedRequest)
            }
            storageService.delete(sha256, storageCredentials)
            val request = CompleteCompressRequest(sha256, storageCredentialsKey, lastModifiedBy)
            archiveClient.completeCompress(request)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): CompressFile {
        return CompressFile(
            id = row[CompressFile::id.name].toString(),
            storageCredentialsKey = row[CompressFile::storageCredentialsKey.name]?.toString(),
            sha256 = row[CompressFile::sha256.name].toString(),
            uncompressedSize = row[CompressFile::uncompressedSize.name].toString().toLong(),
            lastModifiedBy = row[CompressFile::lastModifiedBy.name].toString(),
        )
    }

    override fun entityClass(): KClass<CompressFile> {
        return CompressFile::class
    }

    data class CompressFile(
        var id: String? = null,
        val sha256: String,
        val uncompressedSize: Long,
        val storageCredentialsKey: String?,
        val lastModifiedBy: String,
    )

    private fun listNode(sha256: String, storageCredentialsKey: String?): List<NodeCommonUtils.Node> {
        val query = Query.query(
            Criteria.where("sha256").isEqualTo(sha256)
                .and("compressed").ne(true)
                .and("deleted").isEqualTo(null),
        )
        return NodeCommonUtils.findNodes(query, storageCredentialsKey)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeCompressedJob::class.java)
    }
}
