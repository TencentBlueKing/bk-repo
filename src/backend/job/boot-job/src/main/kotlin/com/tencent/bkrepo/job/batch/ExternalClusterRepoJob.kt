/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.config.PackageDeployJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.replication.api.ExternalClusterClient
import com.tencent.bkrepo.replication.constant.EXTERNAL_CLUSTER_NAME
import com.tencent.bkrepo.replication.pojo.cluster.request.ExternalClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ExternalClusterNodeUpdateRequest
import com.tencent.bkrepo.repository.constant.EXTERNAL_REPO_CONFIG
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.time.LocalDateTime
import java.util.Date

/**
 * 当仓库创建/更新后，进行外部集群信息创建/更新操作
 */
class ExternalClusterRepoJob(
    private val properties: PackageDeployJobProperties,
    private val externalClusterClient: ExternalClusterClient
) : MongoDbBatchJob<ExternalClusterRepoJob.ProxyRepoData>(properties) {

    private val types: List<String>
        get() = properties.types

    @Scheduled(fixedDelay = 60 * 1000L, initialDelay = 60 * 1000L)
    override fun start(): Boolean {
        return super.start()
    }

    override fun entityClass(): Class<ProxyRepoData> {
        return ProxyRepoData::class.java
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        val fromDate = LocalDateTime.now().minusMinutes(1)
        return Query(
            Criteria.where(TYPE).`in`(properties.types)
                .and(LAST_MODIFIED_DATE).gt(fromDate)
        )
    }

    override fun run(row: ProxyRepoData, collectionName: String, context: JobContext) {
        try {
            val config = row.configuration.readJsonString<RepositoryConfiguration>()
            val result = findExternalClusterConfig(config) ?: return
            if (row.createdDate == row.lastModifiedDate) {
                logger.info(
                    "Preparing to send external cluster create request for repo ${row.projectId}|${row.name}."
                )
                // TODO 任务执行类型支持可配
                val externalClusters = result.toJsonString().readJsonString<List<ExternalClusterNodeCreateRequest>>()
                // 每个外部集群配置创建一个集群节点，并创建一个task，这样可以方便集群配置的enable实现
                externalClusters.forEach {
                    buildExternalClusterNodeCreateRequest(row, it)
                }
            } else {
                logger.info(
                    "Preparing to send external cluster update request for repo ${row.projectId}|${row.name}."
                )
                val externalClusters = result.toJsonString().readJsonString<List<ExternalClusterNodeUpdateRequest>>()
                externalClusters.forEach {
                    buildExternalClusterNodeUpdateRequest(row, it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw JobExecuteException(
                "Failed to send external cluster request for repo ${row.projectId}|${row.name}.", e
            )
        }
    }

    private fun buildExternalClusterNodeUpdateRequest(row: ProxyRepoData, request: ExternalClusterNodeUpdateRequest) {
        request.apply {
            this.name = EXTERNAL_CLUSTER_NAME.format(row.projectId, row.name, this.name)
            this.projectId = row.projectId
            this.repoName = row.name
            this.repositoryType = RepositoryType.valueOf(row.type)
        }
        externalClusterClient.updateExternalCluster(request)
    }

    private fun buildExternalClusterNodeCreateRequest(row: ProxyRepoData, request: ExternalClusterNodeCreateRequest) {
        request.apply {
            this.name = EXTERNAL_CLUSTER_NAME.format(row.projectId, row.name, this.name)
            this.projectId = row.projectId
            this.repoName = row.name
            this.repositoryType = RepositoryType.valueOf(row.type)
        }
        externalClusterClient.createExternalCluster(request)
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME = "repository"
    }

    /**
     * 获取外部集群配置信息
     */
    private fun findExternalClusterConfig(configuration: RepositoryConfiguration): Any? {
        return configuration.settings[EXTERNAL_REPO_CONFIG]
    }

    override fun mapToObject(row: Map<String, Any?>): ProxyRepoData {
        return ProxyRepoData(row)
    }

    data class ProxyRepoData(private val map: Map<String, Any?>) {
        val name: String by map
        val projectId: String by map
        val createdDate: Date by map
        val lastModifiedDate: Date by map
        val configuration: String by map
        val type: String by map
    }
}
