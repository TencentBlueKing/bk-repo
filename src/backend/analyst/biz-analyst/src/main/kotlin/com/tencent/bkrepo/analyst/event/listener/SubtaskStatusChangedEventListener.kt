/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.event.listener

import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.model.SubScanTaskDefinition
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.request.ArtifactPlanRelationRequest
import com.tencent.bkrepo.analyst.service.ScanPlanService
import com.tencent.bkrepo.analyst.service.ScanQualityService
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.FORBID_TYPE
import com.tencent.bkrepo.common.artifact.constant.SCAN_STATUS
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.service.metadata.PackageMetadataService
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.metadata.ForbidType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataSaveRequest
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class SubtaskStatusChangedEventListener(
    private val metadataService: MetadataService,
    private val packageMetadataService: PackageMetadataService,
    private val scanPlanService: ScanPlanService,
    private val scanQualityService: ScanQualityService,
    private val lockOperation: LockOperation,
) {
    @Async
    @EventListener(SubtaskStatusChangedEvent::class)
    fun listen(event: SubtaskStatusChangedEvent) {
        with(event.subtask) {
            recordSubtask(event)
            // 未指定扫描方案表示为系统级别触发的扫描，不更新元数据
            if (planId == null) {
                return
            }

            // 加锁避免同一制品的多个扫描方案同时扫描结束时，并发更新禁用状态导致禁用状态错误
            lockOperation.doWithLock("scanner:lock:forbid:$projectId") {
                val metadata = ArrayList<MetadataModel>(4)
                // 更新扫描状态元数据
                addScanStatus(this, metadata)
                modifyForbidMetadata(this, metadata)
                saveMetadata(this, metadata)
                logger.info("update [$projectId/$repoName$fullPath] metadata[$metadata] success")
            }
        }
    }

    /**
     * 打印子任务详情，用于数据分析
     */
    private fun recordSubtask(event: SubtaskStatusChangedEvent) {
        if (SubScanTaskStatus.finishedStatus(event.subtask.status)) {
            val record = createRecord(event)
            logger.info(record.toJsonString().replace(System.lineSeparator(), ""))
        }
    }

    fun addScanStatus(subtask: TPlanArtifactLatestSubScanTask, metadata: ArrayList<MetadataModel>) {
        with(subtask) {
            //状态转换, 存到元数据中
            val artifactPlanStatus = scanPlanService.artifactPlanStatus(
                ArtifactPlanRelationRequest(
                    projectId = projectId,
                    repoName = repoName,
                    repoType = repoType,
                    packageKey = packageKey,
                    version = version,
                    fullPath = fullPath
                )
            ) ?: return
            metadata.add(
                MetadataModel(
                    key = SCAN_STATUS,
                    value = artifactPlanStatus,
                    system = true
                )
            )
        }
    }

    fun saveMetadata(subtask: TPlanArtifactLatestSubScanTask, metadata: ArrayList<MetadataModel>) {
        with(subtask) {
            if (repoType == RepositoryType.GENERIC.name) {
                val request = MetadataSaveRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    nodeMetadata = metadata
                )
                metadataService.saveMetadata(request)
            } else {
                val request = PackageMetadataSaveRequest(
                    projectId = projectId,
                    repoName = repoName,
                    packageKey = packageKey!!,
                    version = version!!,
                    versionMetadata = metadata
                )
                packageMetadataService.saveMetadata(request)
            }
        }
    }

    private fun deleteMetadata(subtask: TPlanArtifactLatestSubScanTask, keysToDelete: Set<String>) {
        with(subtask) {
            if (repoType == RepositoryType.GENERIC.name) {
                metadataService.deleteMetadata(MetadataDeleteRequest(projectId, repoName, fullPath, keysToDelete))
            } else if (!packageKey.isNullOrEmpty() && !version.isNullOrEmpty()) {
                packageMetadataService.deleteMetadata(
                    PackageMetadataDeleteRequest(projectId, repoName, packageKey, version, keysToDelete)
                )
            }
        }
    }

    /**
     * 需要禁用时添加元数据到[metadata]中，不需要禁用时移除禁用相关元数据
     */
    private fun modifyForbidMetadata(subtask: TPlanArtifactLatestSubScanTask, metadata: ArrayList<MetadataModel>) {
        if (!SubScanTaskStatus.finishedStatus(subtask.status)) {
            // 扫描任务未结束时不更新禁用状态元数据
            return
        }
        val projectId = subtask.projectId
        val repoName = subtask.repoName
        val fullPath = subtask.fullPath
        subtask.qualityRedLine?.let {
            metadata.add(MetadataModel(key = SubScanTaskDefinition::qualityRedLine.name, value = it, system = true))
        }
        val currentForbidType = metadataService.listMetadata(projectId, repoName, fullPath)[FORBID_TYPE]
        if (currentForbidType == ForbidType.MANUAL.name) {
            // 手动禁用的情况不处理
            return
        }

        val result = scanQualityService.shouldForbid(projectId, repoName, subtask.repoType, fullPath, subtask.sha256)
        if (result.shouldForbid) {
            // 扫描未通过时添加制品禁用元数据
            val type = ForbidType.valueOf(result.type)
            val user = result.plan?.lastModifiedBy ?: SYSTEM_USER
            metadata.addAll(MetadataUtils.generateForbidMetadata(true, type.reason, type, user))
            return
        } else {
            // 全部方案均通过时移除禁用元数据
            deleteMetadata(subtask, MetadataUtils.FORBID_KEYS)
        }
    }

    private fun createRecord(event: SubtaskStatusChangedEvent): SubtaskRecord {
        val result = event.result
        val metrics = if (event.result is StandardScanExecutorResult) {
            result.output?.metrics
        } else {
            null
        }
        return SubtaskRecord(subtask = event.subtask, metadata = event.taskMetadata, metrics = metrics)
    }

    private data class SubtaskRecord(
        val subtask: TPlanArtifactLatestSubScanTask,
        val metadata: List<TaskMetadata>? = null,
        val metrics: Map<String, Any?>? = null,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(SubtaskStatusChangedEventListener::class.java)
    }
}
