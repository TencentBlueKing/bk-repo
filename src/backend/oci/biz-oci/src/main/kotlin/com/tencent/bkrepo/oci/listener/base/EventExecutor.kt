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

package com.tencent.bkrepo.oci.listener.base

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.oci.listener.pool.EventHandlerThreadPoolExecutor
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor

@Component
class EventExecutor(
    open val nodeClient: NodeClient,
    open val repositoryClient: RepositoryClient,
    open val ociOperationService: OciOperationService
) {
    private val threadPoolExecutor: ThreadPoolExecutor = EventHandlerThreadPoolExecutor.instance

    /**
     * 提交任务到线程池执行
     */
    fun submit(
        event: ArtifactEvent
    ): Future<Boolean> {
        return threadPoolExecutor.submit<Boolean> {
            try {
                eventHandler(event)
                true
            } catch (exception: Throwable) {
                logger.warn("Error occurred while executing the oci event: $exception")
                false
            }
        }
    }

    private fun eventHandler(event: ArtifactEvent) {
        when (event.type) {
            EventType.REPLICATION_THIRD_PARTY -> replicationEventHandler(event)
            EventType.REPO_CREATED, EventType.REPO_REFRESHED, EventType.REPO_UPDATED -> {
                ociOperationService.getPackagesFromThirdPartyRepo(event.projectId, event.repoName)
            }
            else -> throw UnsupportedOperationException()
        }
    }

    private fun replicationEventHandler(event: ArtifactEvent) {
        with(event) {
            val packageName = event.data["packageName"].toString()
            val version = event.data["version"].toString()
            val sha256 = event.data["sha256"].toString()
            val ociArtifactInfo = OciManifestArtifactInfo(
                projectId, repoName, packageName, "", version, false
            )
            val nodeInfo = nodeClient.getNodeDetail(projectId, repoName, ociArtifactInfo.getArtifactFullPath()).data
                ?: throw NodeNotFoundException(
                    "${ociArtifactInfo.getArtifactFullPath()} not found in repo in $projectId|$repoName"
                )
            val ociDigest = OciDigest.fromSha256(sha256)
            val repositoryDetail = repositoryClient.getRepoDetail(projectId, repoName).data
                ?: throw RepoNotFoundException("$projectId|$repoName")
            ociOperationService.updateOciInfo(
                ociArtifactInfo = ociArtifactInfo,
                digest = ociDigest,
                nodeDetail = nodeInfo,
                storageCredentials = repositoryDetail.storageCredentials,
                sourceType = ArtifactChannel.REPLICATION
            )
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(EventExecutor::class.java)
    }
}
