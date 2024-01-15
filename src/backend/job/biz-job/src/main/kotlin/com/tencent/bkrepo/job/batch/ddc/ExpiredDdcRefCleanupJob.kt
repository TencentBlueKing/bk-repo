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
import org.bson.types.Binary
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Component
@EnableConfigurationProperties(ExpiredDdcRefCleanupJobProperties::class)
class ExpiredDdcRefCleanupJob(
    private val properties: ExpiredDdcRefCleanupJobProperties,
    private val nodeClient: NodeClient,
) : DefaultContextMongoDbJob<ExpiredDdcRefCleanupJob.Ref>(properties) {
    override fun collectionNames(): List<String> = listOf(COLLECTION_NAME, COLLECTION_NAME_LEGACY)

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun buildQuery(): Query {
        val cutoffTime = LocalDateTime.now().minus(properties.expired)
        val query = Query.query(Criteria.where("lastAccessDate").lt(cutoffTime))
        query.fields().include(ID, Ref::projectId.name, Ref::repoName.name, Ref::bucket.name, Ref::key.name)
        return query
    }

    override fun mapToEntity(row: Map<String, Any?>): Ref {
        return Ref(
            row[ID]!!.toString(),
            row[Ref::projectId.name]!!.toString(),
            row[Ref::repoName.name]!!.toString(),
            row[Ref::bucket.name]!!.toString(),
            row[Ref::key.name]!!.toString(),
            row[Ref::inlineBlob.name] as Binary?,
        )
    }

    override fun entityClass(): KClass<Ref> = Ref::class

    override fun run(row: Ref, collectionName: String, context: JobContext) {
        // 清理过期ref
        mongoTemplate.remove(Query(Criteria.where(ID).isEqualTo(row.id)), collectionName)
        if (row.inlineBlob == null && collectionName == COLLECTION_NAME) {
            // inlineBlob为null时表示inlineBlob不存在数据库中而是单独存放于后端存储中，需要一并清理
            nodeClient.deleteNode(
                NodeDeleteRequest(row.projectId, row.repoName, "/${row.bucket}/${row.key}", SYSTEM_USER)
            )
        }

        // 从blob ref列表中移除ref
        val refKey = "ref/${row.bucket}/${row.key}"
        val criteria = Criteria
            .where(DdcBlobCleanupJob.Blob::projectId.name).isEqualTo(row.projectId)
            .and(DdcBlobCleanupJob.Blob::repoName.name).isEqualTo(row.repoName)
            .and(DdcBlobCleanupJob.Blob::references.name).inValues(refKey)
        val update = Update().pull(DdcBlobCleanupJob.Blob::references.name, refKey)
        mongoTemplate.updateMulti(Query(criteria), update, DdcBlobCleanupJob.COLLECTION_NAME)
    }

    data class Ref(
        val id: String,
        val projectId: String,
        val repoName: String,
        val bucket: String,
        val key: String,
        val inlineBlob: Binary? = null
    )

    companion object {
        const val COLLECTION_NAME = "ddc_ref"
        const val COLLECTION_NAME_LEGACY = "ddc_legacy_ref"
    }
}
