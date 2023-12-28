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

package com.tencent.bkrepo.helm.listener.base

import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.helm.listener.event.ChartUploadEvent
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.chart.ChartUploadRequest
import com.tencent.bkrepo.helm.service.impl.HelmOperationService
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RemoteEventJobExecutor(
    private val helmOperationService: HelmOperationService,
    private val packageClient: PackageClient
) : AbstractEventJobExecutor() {
    /**
     * 执行同步
     */
    fun execute(event: ArtifactEvent) {
        try {
            logger.info("Will start to handle event $event")
            with(event) {
                val action: () -> Unit = when (type) {
                    EventType.REPO_CREATED, EventType.REPO_UPDATED, EventType.REPO_REFRESHED -> {
                        {
                            helmOperationService.lockAction(projectId, repoName) {
                                helmOperationService.updatePackageForRemote(projectId, repoName, userId)
                            }
                        }
                    }
                    EventType.VERSION_CREATED, EventType.VERSION_UPDATED -> {
                        {
                            handlePackageVersionEvent(event)
                        }
                    }
                    else -> { {} }
                }
                submit(action)
                logger.info("Helm Remote event ${getFullResourceKey()} completed.")
            }
        } catch (exception: Exception) {
            logger.warn("Helm Remote event ${event.getFullResourceKey()}} failed: $exception")
        }
    }

    private fun handlePackageVersionEvent(event: ArtifactEvent) {
        val packageType = event.data["packageType"].toString()
        if (packageType != PackageType.HELM.name) return
        val packageKey = event.data["packageKey"].toString()
        val version = event.data["packageVersion"].toString()
        val packageName = event.data["packageName"].toString()
        val packageVersion = packageClient.findVersionByName(
            event.projectId, event.repoName, packageKey, version
        ).data ?: return
        if (packageVersion.metadata[SOURCE_TYPE] != ArtifactChannel.REPLICATION.name) return
        val fullPath = HelmUtils.getChartFileFullPath(packageName, version)
        SpringContextUtils.publishEvent(buildChartUploadEvent(
            projectId = event.projectId,
            repoName = event.repoName,
            fullPath = fullPath,
            packageName = packageName,
            packageVersion = packageVersion
        ))
    }

    private fun buildChartUploadEvent(
        projectId: String, repoName: String, fullPath: String,
        packageName: String, packageVersion: PackageVersion
    ): ChartUploadEvent {
        return ChartUploadEvent(
            ChartUploadRequest(
                projectId = projectId,
                repoName = repoName,
                name = packageName,
                version = packageVersion.name,
                operator = SYSTEM_USER,
                fullPath = fullPath,
                metadataMap = packageVersion.metadata,
                artifactInfo = HelmArtifactInfo(projectId, repoName, fullPath)
            )
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteEventJobExecutor::class.java)
    }
}
