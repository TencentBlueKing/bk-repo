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

package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.constant.COMMIT_EDGE_HEADER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.message.MessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.slf4j.LoggerFactory
import org.springframework.util.AntPathMatcher

/**
 * 集群操作工具类
 */
object ClusterUtils {

    private val logger = LoggerFactory.getLogger(ClusterUtils::class.java)
    private val matcher = AntPathMatcher()

    val nodeLevelNotFoundError = listOf(
        ArtifactMessageCode.NODE_NOT_FOUND,
        ArtifactMessageCode.REPOSITORY_NOT_FOUND,
        ArtifactMessageCode.PROJECT_NOT_FOUND
    )

    val repoLevelNotFoundError = listOf(ArtifactMessageCode.REPOSITORY_NOT_FOUND, ArtifactMessageCode.PROJECT_NOT_FOUND)

    /**
     * 检查请求来源cluster是否是资源的唯一拥有者
     */
    fun checkIsSrcCluster(clusterNames: Set<String>?) {
        if (!isUniqueSrcCluster(clusterNames)) {
            throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
        }
    }

    /**
     * 检查请求来源cluster是否是资源的拥有者
     */
    fun checkContainsSrcCluster(clusterNames: Set<String>?) {
        if (!containsSrcCluster(clusterNames)) {
            throw ErrorCodeException(CommonMessageCode.OPERATION_CROSS_CLUSTER_NOT_ALLOWED)
        }
    }

    /**
     * 判断请求来源cluster是否拥有资源
     */
    fun containsSrcCluster(clusterNames: Set<String>?): Boolean {
        val clusterProperties = SpringContextUtils.getBean<ClusterProperties>()
        var srcCluster = SecurityUtils.getClusterName()

        if (clusterNames == null && srcCluster.isNullOrBlank()) {
            // 兼容旧逻辑
            return true
        } else if (clusterNames == null) {
            // edge plus操作center节点
            return false
        } else if (srcCluster.isNullOrBlank()) {
            // center操作节点
            srcCluster = clusterProperties.self.name
        }

        return clusterNames.contains(srcCluster)
    }

    /**
     * 判断请求来源cluster是否是资源的唯一拥有者
     */
    fun isUniqueSrcCluster(clusterNames: Set<String>?): Boolean {
        return containsSrcCluster(clusterNames) && (clusterNames == null || clusterNames.size <= 1)
    }

    /**
     * 判断元数据是否上报到center
     */
    fun reportMetadataToCenter(projectId: String, repoName: String? = null): Boolean {
        logger.debug("report to center: ${HeaderUtils.getHeader(COMMIT_EDGE_HEADER)}")
        return if (repoName == null) {
            HeaderUtils.getHeader(COMMIT_EDGE_HEADER)?.toBoolean() == true
        } else {
            HeaderUtils.getHeader(COMMIT_EDGE_HEADER)?.toBoolean() == true && enabledCommitEdge(projectId, repoName)
        }
    }

    /**
     * 判断是否为Edge节点的请求
     */
    fun isEdgeRequest(): Boolean {
        return !SecurityUtils.getClusterName().isNullOrEmpty()
    }

    /**
     * 判断仓库是否启用commit-edge
     */
    private fun enabledCommitEdge(projectId: String, repoName: String): Boolean {
        val clusterProperties = SpringContextUtils.getBean<ClusterProperties>()
        val enabledRepos = clusterProperties.commitEdge.repo.enabledRepoList
        enabledRepos.forEach {
            if (matcher.match(it, "$projectId/$repoName")) {
                return true
            }
        }
        return false
    }

    /**
     * Edge请求Center时，忽略特定错误
     */
    fun ignoreException(
        projectId: String,
        repoName: String,
        messageCodes: List<MessageCode> = emptyList(),
        function: () -> Response<Any>
    ) {
        try {
            if (enabledCommitEdge(projectId, repoName)) {
                function.invoke()
                return
            }
        } catch (e: RemoteErrorCodeException) {
            if (!messageCodes.map { it.getCode() }.contains(e.errorCode)) {
                throw e
            }
        }
    }
}
