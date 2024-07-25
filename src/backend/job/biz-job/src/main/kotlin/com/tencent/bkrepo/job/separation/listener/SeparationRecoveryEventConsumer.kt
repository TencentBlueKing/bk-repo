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

package com.tencent.bkrepo.job.separation.listener

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.job.RESTORE
import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.dao.SeparationNodeDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageDao
import com.tencent.bkrepo.job.separation.dao.SeparationPackageVersionDao
import com.tencent.bkrepo.job.separation.model.TSeparationNode
import com.tencent.bkrepo.job.separation.model.TSeparationPackageVersion
import com.tencent.bkrepo.job.separation.pojo.NodeFilterInfo
import com.tencent.bkrepo.job.separation.pojo.PackageFilterInfo
import com.tencent.bkrepo.job.separation.pojo.RecoveryNodeInfo
import com.tencent.bkrepo.job.separation.pojo.RecoveryVersionInfo
import com.tencent.bkrepo.job.separation.pojo.SeparationContent
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskRequest
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.job.separation.service.impl.repo.RepoSpecialSeparationMappings
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

/**
 * 消费降冷自动恢复事件
 */
@Component("separationRecovery")
class SeparationRecoveryEventConsumer(
    private val separationTaskService: SeparationTaskService,
    private val dataSeparationConfig: DataSeparationConfig,
    private val separationPackageDao: SeparationPackageDao,
    private val separationPackageVersionDao: SeparationPackageVersionDao,
    private val separationNodeDao: SeparationNodeDao
) : Consumer<Message<ArtifactEvent>> {

    /**
     * 允许接收的事件类型
     */
    private val acceptTypes = setOf(
        EventType.NODE_SEPARATION_RECOVERY,
    )

    override fun accept(message: Message<ArtifactEvent>) {
        if (!dataSeparationConfig.enableAutoRecovery) return
        if (!acceptTypes.contains(message.payload.type)) {
            return
        }
        logger.info("current separation recovery message header is ${message.headers}")
        doSeparationRecovery(message.payload)
    }

    private fun doSeparationRecovery(event: ArtifactEvent) {
        val recoveryNodeInfo = buildRecoveryNodeInfo(event)
        val task = when (recoveryNodeInfo.repoType) {
            RepositoryType.MAVEN.name -> {
                val recoveryVersionInfo = RepoSpecialSeparationMappings.getRecoveryPackageVersionData(recoveryNodeInfo)
                val version = recoveryVersionCheck(recoveryVersionInfo) ?: return
                buildVersionSeparationTaskRequest(recoveryVersionInfo, version)
            }
            RepositoryType.GENERIC.name -> {
                val node = recoveryNodeCheck(recoveryNodeInfo) ?: return
                buildNodeSeparationTaskRequest(recoveryNodeInfo, node)
            }
            else -> {
                null
            }
        }
        if (task != null) {
            separationTaskService.createSeparationTask(task)
        }
    }

    private fun buildRecoveryNodeInfo(event: ArtifactEvent): RecoveryNodeInfo {
        return RecoveryNodeInfo(
            projectId = event.projectId,
            repoName = event.repoName,
            fullPath = event.resourceKey,
            repoType = event.data["repoType"].toString()
        )
    }

    private fun recoveryNodeCheck(recoveryNodeInfo: RecoveryNodeInfo): TSeparationNode? {
        with(recoveryNodeInfo) {
            val separateDates = separationTaskService.findDistinctSeparationDate(projectId, repoName)
            if (separateDates.isEmpty()) return null
            separateDates.forEach {
                val separationNode = separationNodeDao.findOneByFullPath(projectId, repoName, fullPath, it)
                if (separationNode != null) {
                    return separationNode
                }
            }
            return null
        }
    }

    private fun recoveryVersionCheck(recoveryVersionInfo: RecoveryVersionInfo): TSeparationPackageVersion? {
        with(recoveryVersionInfo) {
            val separateDates = separationTaskService.findDistinctSeparationDate(projectId, repoName)
            if (separateDates.isEmpty()) return null
            val separationPackage = separationPackageDao.findByKey(projectId, repoName, packageKey) ?: return null
            separateDates.forEach {
                val version = separationPackageVersionDao.findByName(separationPackage.id!!, version, it)
                if (version != null) {
                    return version
                }
            }
            return null
        }
    }

    private fun buildVersionSeparationTaskRequest(
        recoveryVersionInfo: RecoveryVersionInfo,
        separationPackageVersion: TSeparationPackageVersion
    ): SeparationTaskRequest {
        return SeparationTaskRequest(
            projectId = recoveryVersionInfo.projectId,
            repoName = recoveryVersionInfo.repoName,
            type = RESTORE,
            separateAt = separationPackageVersion.separationDate.format(DateTimeFormatter.ISO_DATE_TIME),
            content = buildSeparationContent(recoveryVersionInfo.packageKey, separationPackageVersion.name)!!
        )
    }

    private fun buildNodeSeparationTaskRequest(
        recoveryNodeInfo: RecoveryNodeInfo,
        separationNode: TSeparationNode
    ): SeparationTaskRequest {
        return SeparationTaskRequest(
            projectId = recoveryNodeInfo.projectId,
            repoName = recoveryNodeInfo.repoName,
            type = RESTORE,
            separateAt = separationNode.separationDate.format(DateTimeFormatter.ISO_DATE_TIME),
            content = buildSeparationContent(fullPath = separationNode.fullPath)!!
        )
    }

    private fun buildSeparationContent(
        packageKey: String? = null,
        version: String? = null,
        fullPath: String? = null
    ): SeparationContent? {
        if (packageKey.isNullOrEmpty() && version.isNullOrEmpty() && fullPath.isNullOrEmpty()) return null
        return if (fullPath.isNullOrEmpty()) {
            SeparationContent(
                packages = mutableListOf(
                    PackageFilterInfo(
                        packageKey = packageKey,
                        versions = listOf(version!!)
                    )
                )
            )
        } else {
            SeparationContent(
                paths = mutableListOf(
                    NodeFilterInfo(
                        path = fullPath
                    )
                )
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SeparationRecoveryEventConsumer::class.java)
    }
}