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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.helm.api.HelmClient
import com.tencent.bkrepo.job.CATEGORY
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.HelmMetadataRefreshJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass

/**
 * 读取helm仓库对应节点的chart.yaml信息写入对应节点元数据中，主要用于历史数据刷新
 */
@Component
@EnableConfigurationProperties(HelmMetadataRefreshJobProperties::class)
class HelmMetadataRefreshJob(
    private val properties: HelmMetadataRefreshJobProperties,
    private val helmClient: HelmClient
) : DefaultContextMongoDbJob<HelmMetadataRefreshJob.Repository>(properties) {


    override fun entityClass(): KClass<Repository> {
        return Repository::class
    }

    override fun collectionNames(): List<String> {
        return listOf(REPOSITORY_COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query(
            Criteria.where(TYPE).`is`(RepositoryType.HELM.name)
                .and(CATEGORY).ne(RepositoryCategory.REMOTE.name)
        )
    }


    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun run(row: Repository, collectionName: String, context: JobContext) {
        with(row) {
            try {
                val config = row.configuration.readJsonString<RepositoryConfiguration>()
                val metaDataRefreshStatus = config.getSetting(META_DATA_REFRESH_CONFIG)?.toBoolean() ?: false
                if (metaDataRefreshStatus) return
                helmClient.metadataRefresh(projectId, name)
            } catch (e: Exception) {
                logger.warn("Failed to send metadata refresh request " +
                                "for repo ${row.projectId}|${row.name}, error: ${e.message}", e)
            }
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): Repository {
        return Repository(row)
    }


    data class Repository(
        var projectId: String,
        var name: String,
        var type: String,
        var configuration: String,
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Repository::projectId.name].toString(),
            map[Repository::name.name].toString(),
            map[Repository::type.name].toString(),
            map[Repository::configuration.name].toString(),
        )
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val REPOSITORY_COLLECTION_NAME = "repository"
        private const val META_DATA_REFRESH_CONFIG = "metaDataRefreshStatus"

    }
}
