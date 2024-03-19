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

package com.tencent.bkrepo.job.batch.task.cache

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.cache.indexer.StorageCacheIndexerManager
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.StorageCacheIndexSyncJobProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration

abstract class StorageCacheIndexJob(
    private val properties: StorageCacheIndexSyncJobProperties,
    private val storageProperties: StorageProperties,
    private val clusterProperties: ClusterProperties,
    private val mongoTemplate: MongoTemplate,
    protected val indexerManager: ObjectProvider<StorageCacheIndexerManager>
) : DefaultContextJob(properties) {

    private data class TStorageCredentials(
        val id: String,
        val credentials: String,
        val region: String? = null
    )

    override fun getLockAtMostFor(): Duration = Duration.ofHours(1)

    override fun doStart0(jobContext: JobContext) {
        doWithCredentials(storageProperties.defaultStorageCredentials())
        mongoTemplate.find(Query(), TStorageCredentials::class.java, "storage_credentials")
            .filter { clusterProperties.region.isNullOrBlank() || it.region == clusterProperties.region }
            .filter { it.id !in properties.ignoredStorageCredentialsKeys }
            .map { it.credentials.readJsonString<StorageCredentials>().apply { key = it.id } }
            .forEach { doWithCredentials(it) }
    }

    abstract fun doWithCredentials(credentials: StorageCredentials)
}
