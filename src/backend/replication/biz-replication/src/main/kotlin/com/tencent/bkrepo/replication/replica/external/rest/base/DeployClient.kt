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

package com.tencent.bkrepo.replication.replica.external.rest.base

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * 制品推送到远端仓库
 */
abstract class DeployClient(
    private val localDataManager: LocalDataManager,
    private val replicationProperties: ReplicationProperties
) {
    lateinit var deployClient: OkHttpClient

    lateinit var clusterInfo: RemoteClusterInfo

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
    fun deployArtifact(name: String, version: String, projectId: String, repoName: String): Boolean {
        logger.info(
            "Package $name|$version in the local repo $projectId|$repoName will be deployed to the external repository"
        )
        val token = buildAuthHandler(name)
        val nodes = syncNodeList(
            name = name,
            version = version,
            projectId = projectId,
            repoName = repoName
        )
        if (nodes.isEmpty()) return true
        return uploadPackage(
            token = token,
            name = name,
            version = version,
            nodes = nodes
        )
    }

    /**
     * 获取需要上传的文件，并上传
     */
    open fun uploadPackage(
        nodes: List<NodeDetail>,
        name: String,
        version: String,
        token: String?
    ): Boolean {
        return true
    }

    /**
     * 获取auth处理器
     */
    open fun buildAuthHandler(name: String): String? {
        return AuthHandler().obtainToken()
    }

    /**
     * 获取需要同步节点列表
     */
    open fun syncNodeList(name: String, version: String, projectId: String, repoName: String): List<NodeDetail> {
        return emptyList()
    }

    /**
     * 读取节点数据流
     */
    fun loadInputStream(nodeInfo: NodeDetail): InputStream {
        with(nodeInfo) {
            return loadInputStream(sha256!!, size, projectId, repoName)
        }
    }

    /**
     * 读取节点数据流
     */
    fun loadInputStream(sha256: String, size: Long, projectId: String, repoName: String): InputStream {
        val repo = localDataManager.findRepoByName(projectId, repoName)
        val artifactInputStream = localDataManager.getBlobData(sha256, size, repo)
        return artifactInputStream.rateLimit(replicationProperties.rateLimit.toBytes())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeployClient::class.java)
    }
}
