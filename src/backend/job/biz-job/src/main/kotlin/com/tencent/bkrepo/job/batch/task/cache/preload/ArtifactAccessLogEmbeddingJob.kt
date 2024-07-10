/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.cache.preload

import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.AiProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusServiceClientProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusVectorStoreProperties
import com.tencent.bkrepo.job.config.properties.ArtifactAccessLogEmbeddingJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetrics
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
@EnableConfigurationProperties(
    ArtifactAccessLogEmbeddingJobProperties::class,
    AiProperties::class,
    MilvusVectorStoreProperties::class,
    MilvusServiceClientProperties::class
)
class ArtifactAccessLogEmbeddingJob(
    properties: ArtifactAccessLogEmbeddingJobProperties
) : DefaultContextMongoDbJob<TProjectMetrics>(properties) {
    override fun collectionNames(): List<String> {
        TODO("Not yet implemented")
    }

    override fun buildQuery(): Query {
        TODO("Not yet implemented")
    }

    override fun mapToEntity(row: Map<String, Any?>): TProjectMetrics {
        TODO("Not yet implemented")
    }

    override fun entityClass(): KClass<TProjectMetrics> {
        TODO("Not yet implemented")
    }

    override fun run(row: TProjectMetrics, collectionName: String, context: JobContext) {
        TODO("Not yet implemented")
    }
}
