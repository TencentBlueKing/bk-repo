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

package com.tencent.bkrepo.replication.replica.repository.remote.base

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.replica.base.interceptor.RetryInterceptor
import com.tencent.bkrepo.replication.replica.base.interceptor.progress.ProgressInterceptor
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * 制品推送到远端仓库
 */
abstract class PushClient(
    val replicationProperties: ReplicationProperties,
    val localDataManager: LocalDataManager
) {
    val httpClient: OkHttpClient = buildClient()

    /**
     * 匹配仓库类型
     */
    abstract fun type(): RepositoryType

    /**
     * 额外兼容仓库类型
     */
    open fun extraType(): RepositoryType? {
        return null
    }

    /**
     * 推送制品
     */
    fun pushArtifact(
        name: String,
        version: String,
        projectId: String,
        repoName: String,
        context: ReplicaContext
    ): Boolean {
        logger.info(
            "Package $name|$version in the local repo $projectId|$repoName will be pushed to the third party repository"
        )
        try {
            val token = getAuthorizationDetails(name, context.cluster)
            val nodes = querySyncNodeList(
                name = name,
                version = version,
                projectId = projectId,
                repoName = repoName
            )
            if (nodes.isEmpty()) return true
            return processToUploadArtifact(
                token = token,
                name = name,
                version = version,
                nodes = nodes,
                context = context
            )
        } catch (e: Exception) {
            logger.warn(
                "Error occurred while pushing artifact $name|$version to cluster[${context.cluster.name}] " +
                    "in the local repo $projectId|$repoName, failed reason: ${e.message}"
            )
            throw e
        }
    }

    /**
     * 上传
     */
    open fun processToUploadArtifact(
        nodes: List<NodeDetail>,
        name: String,
        version: String,
        token: String?,
        context: ReplicaContext
    ): Boolean {
        return true
    }

    /**
     * 获取授权详情-Authorization token
     */
    open fun getAuthorizationDetails(name: String, clusterInfo: ClusterInfo): String? {
        return null
    }

    /**
     * 获取需要同步节点列表
     */
    open fun querySyncNodeList(name: String, version: String, projectId: String, repoName: String): List<NodeDetail> {
        return emptyList()
    }

    protected fun buildRequestTag(
        context: ReplicaContext,
        key: String,
        size: Long
    ): RequestTag? {
        return if (context.task.replicaType == ReplicaType.RUN_ONCE) {
            RequestTag(
                task = context.task,
                key = key,
                size = size
            )
        } else {
            null
        }
    }

    private fun buildClient(): OkHttpClient {
        return HttpClientBuilderFactory.create()
            .protocols(listOf(Protocol.HTTP_1_1))
            .readTimeout(DEFAULT_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .addInterceptor(RetryInterceptor())
            .addNetworkInterceptor(ProgressInterceptor())
            .build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PushClient::class.java)
        private const val DEFAULT_READ_TIMEOUT_MINUTES = 15L
        private const val DEFAULT_CONNECT_TIMEOUT_MINUTES = 1L
        private const val DEFAULT_WRITE_TIMEOUT_MINUTES = 1L
    }
}
