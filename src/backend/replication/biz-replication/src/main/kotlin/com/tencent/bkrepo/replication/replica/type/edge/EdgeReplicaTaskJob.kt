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

package com.tencent.bkrepo.replication.replica.type.edge

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.common.service.util.UrlUtils
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaTaskClient
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.task.EdgeReplicaTaskRecord
import com.tencent.bkrepo.replication.util.OkHttpClientPool
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.executor.ManualThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.base.interceptor.SignInterceptor
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import javax.annotation.PostConstruct

@Component
@Conditional(CommitEdgeEdgeCondition::class)
class EdgeReplicaTaskJob(
    private val clusterProperties: ClusterProperties,
    private val replicationProperties: ReplicationProperties,
    private val nodeClient: NodeClient,
    private val packageClient: PackageClient
) {

    private val centerReplicaTaskClient: ClusterReplicaTaskClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "replication", clusterProperties.self.name) }
    private val okhttpClient = OkHttpClientPool.getHttpClient(
        replicationProperties.timoutCheckHosts,
        clusterProperties.center,
        Duration.ofSeconds(30),
        Duration.ofSeconds(30),
        Duration.ZERO,
        SignInterceptor(clusterProperties.center)
    )
    private val executor = ManualThreadPoolExecutor.instance

    @PostConstruct
    fun run() {
        Thread {
            while (true) {
                try {
                    if (executor.activeCount == executor.maximumPoolSize) {
                        Thread.sleep(5000)
                        logger.info("executing replica task count is ${executor.maximumPoolSize}, " +
                            "stop claim task from center")
                        continue
                    }
                    claimTaskFromCenter()
                } catch (e: Exception) {
                    logger.error("execute replica task error: ", e)
                }
            }
        }.start()
    }

    private fun claimTaskFromCenter() {
        val url = UrlUtils.extractDomain(clusterProperties.center.url)
            .plus("/replication/cluster/task/edge/claim" +
                "?clusterName=${clusterProperties.self.name}&replicatingNum=${executor.activeCount}")
        val request = Request.Builder().url(url).get().build()
        try {
            okhttpClient.newCall(request).execute().use {
                handleResponse(it)
            }
        } catch (_: SocketTimeoutException) {
            return
        } catch (e: IOException) {
            logger.error("get edge replica task failed: ", e)
            return
        }
    }

    private fun handleResponse(it: okhttp3.Response) {
        if (it.isSuccessful) {
            val taskRecord = it.body!!.string().readJsonString<Response<EdgeReplicaTaskRecord>>().data!!
            if (!taskRecord.fullPath.isNullOrEmpty()) {
                executor.execute(Runnable { replicaFile(taskRecord) }.trace())
            }
            if (!taskRecord.packageKey.isNullOrEmpty() && !taskRecord.packageVersion.isNullOrEmpty()) {
                executor.execute(Runnable { replicaPackageVersion(taskRecord) }.trace())
            }
        } else if (it.code == HttpStatus.NOT_MODIFIED.value) {
            // do nothing
        } else {
            logger.error("get edge replica task failed: ${it.code}, ${it.body?.string()}")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun replicaFile(edgeReplicaTaskRecord: EdgeReplicaTaskRecord) {
        with(edgeReplicaTaskRecord) {
            logger.info("start to replica file[$projectId/$repoName$fullPath]")
            val replicaContext = ReplicaContext(
                taskDetail = taskDetail,
                taskObject = taskObject,
                taskRecord = taskRecord,
                localRepo = localRepo,
                remoteCluster = remoteCluster,
                replicationProperties = replicationProperties
            )
            try {
                val nodeInfo = nodeClient.getNodeDetail(projectId, repoName, fullPath!!).data?.nodeInfo
                    ?: throw NodeNotFoundException(fullPath!!)
                replicaContext.replicator.replicaFile(replicaContext, nodeInfo)
                status = ExecutionStatus.SUCCESS
            } catch (e: Exception) {
                logger.error("replica file error: ", e)
                status = ExecutionStatus.FAILED
                errorReason = e.localizedMessage
            } finally {
                centerReplicaTaskClient.reportEdgeReplicaTaskResult(this)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun replicaPackageVersion(edgeReplicaTaskRecord: EdgeReplicaTaskRecord) {
        with(edgeReplicaTaskRecord) {
            logger.info("start to replica package version[$projectId/$repoName/$packageKey/$packageVersion]")
            val replicaContext = ReplicaContext(
                taskDetail = taskDetail,
                taskObject = taskObject,
                taskRecord = taskRecord,
                localRepo = localRepo,
                remoteCluster = remoteCluster,
                replicationProperties = replicationProperties
            )
            try {
                val packageSummary = packageClient.findPackageByKey(projectId, repoName, packageKey!!).data
                    ?: throw PackageNotFoundException(packageKey!!)
                val packageVersion =
                    packageClient.findVersionByName(projectId, repoName, packageKey!!, packageVersion!!).data
                        ?: throw VersionNotFoundException(packageVersion!!)
                replicaContext.replicator.replicaPackageVersion(replicaContext, packageSummary, packageVersion)
                status = ExecutionStatus.SUCCESS
            } catch (e: Exception) {
                logger.error("replica package version error: ", e)
                status = ExecutionStatus.FAILED
                errorReason = e.localizedMessage
            } finally {
                centerReplicaTaskClient.reportEdgeReplicaTaskResult(this)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EdgeReplicaTaskJob::class.java)
    }
}
