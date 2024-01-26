/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.ddc

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.exists
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.size
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@EnableConfigurationProperties(DdcBlobCleanupJobProperties::class)
class DdcBlobCleanupJob(
    private val properties: DdcBlobCleanupJobProperties,
    private val nodeClient: NodeClient,
) : DefaultContextMongoDbJob<DdcBlobCleanupJob.Blob>(properties) {
    override fun collectionNames() = listOf(COLLECTION_NAME)

    override fun buildQuery(): Query {
        val referencesCriteria = Criteria().orOperator(
            Blob::references.exists(false),
            Blob::references.size(0)
        )
        // 最近1小时上传的blob不清理，避免将未finalized的ref所引用的blob清理掉
        val criteria = Criteria().andOperator(
            referencesCriteria,
            Criteria.where("lastModifiedDate").lt(LocalDateTime.now().minusHours(1L))
        )
        val query = Query(criteria)
        query.fields().include(ID, Blob::projectId.name, Blob::repoName.name, Blob::blobId.name)
        return query
    }

    override fun mapToEntity(row: Map<String, Any?>): Blob {
        return Blob(
            id = row[ID]!!.toString(),
            projectId = row[Blob::projectId.name]!!.toString(),
            repoName = row[Blob::repoName.name]!!.toString(),
            blobId = row[Blob::blobId.name]!!.toString()
        )
    }

    override fun entityClass() = Blob::class

    override fun run(row: Blob, collectionName: String, context: JobContext) {
        nodeClient.deleteNode(
            NodeDeleteRequest(
                projectId = row.projectId,
                repoName = row.repoName,
                fullPath = "/blobs/${row.blobId}",
                operator = SYSTEM_USER
            )
        )
        mongoTemplate.remove(Query(Criteria.where(ID).isEqualTo(row.id)), collectionName)
    }

    data class Blob(
        val id: String,
        val projectId: String,
        val repoName: String,
        val blobId: String,
        val references: Set<String> = emptySet()
    )

    companion object {
        const val COLLECTION_NAME = "ddc_blob"
    }
}
