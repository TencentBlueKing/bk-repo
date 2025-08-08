/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.ddc

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.DdcBlobRefCountCorrectJobContext
import com.tencent.bkrepo.job.batch.task.ddc.DdcBlobCleanupJob.Blob
import com.tencent.bkrepo.job.batch.task.ddc.DdcBlobCleanupJob.Companion.COLLECTION_NAME
import com.tencent.bkrepo.job.batch.task.ddc.ExpiredDdcRefCleanupJob.Companion.COLLECTION_NAME_BLOB_REF
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.config.properties.DdcBlobRefCountCorrectJobProperties
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * DDC blob 引用数校正任务
 * 仅处理blob-ref关系已删除，但是blob.refCount不为0的情况
 */
@Component
@Suppress("UnstableApiUsage")
class DdcBlobRefCountCorrectJob(
    private val properties: DdcBlobRefCountCorrectJobProperties,
) : MongoDbBatchJob<Blob, DdcBlobRefCountCorrectJobContext>(properties) {

    override fun createJobContext(): DdcBlobRefCountCorrectJobContext {
        val bf = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            properties.expectedRefs,
            properties.fpp,
        )
        logger.info("Start to build ref bloom filter")
        val query = Query()
        query.fields().include(ExpiredDdcRefCleanupJob.BlobRef::blobId.name)
        NodeCommonUtils.findByCollection(Query(), BATCH_SIZE, COLLECTION_NAME_BLOB_REF) {
            bf.put(it[ExpiredDdcRefCleanupJob.BlobRef::blobId.name]!!.toString())
        }
        val count = "approximate(${bf.approximateElementCount()})/expected(${properties.expectedRefs})"
        logger.info("Build ref bloom filter successful, count: $count, fpp: ${bf.expectedFpp()}")
        return DdcBlobRefCountCorrectJobContext(bf)
    }

    override fun run(row: Blob, collectionName: String, context: DdcBlobRefCountCorrectJobContext) {
        if (row.refCount == 0L || context.bf.mightContain(row.blobId)) {
            // blob引用已经为0，或者blob引用大概率存在时不处理直接返回
            return
        }

        // blob未被引用，更新refCount为0
        val criteria = Criteria
            .where(Blob::projectId.name).isEqualTo(row.projectId)
            .and(Blob::repoName.name).isEqualTo(row.repoName)
            .and(Blob::blobId.name).isEqualTo(row.blobId)
        val update = Update().set(Blob::refCount.name, 0L)
        mongoTemplate.updateFirst(Query(criteria), update, COLLECTION_NAME)
    }

    override fun collectionNames() = listOf(COLLECTION_NAME)

    override fun buildQuery() = Query()

    override fun mapToEntity(row: Map<String, Any?>): Blob {
        return Blob(
            id = row[ID]!!.toString(),
            projectId = row[Blob::projectId.name]!!.toString(),
            repoName = row[Blob::repoName.name]!!.toString(),
            blobId = row[Blob::blobId.name]!!.toString(),
            references = (row[Blob::references.name] as? List<String>)?.toSet() ?: emptySet(),
            refCount = row[Blob::refCount.name]?.toString()?.toLong() ?: 0L,
        )
    }

    override fun entityClass() = Blob::class

    companion object {
        private val logger = LoggerFactory.getLogger(DdcBlobRefCountCorrectJobContext::class.java)
    }
}
