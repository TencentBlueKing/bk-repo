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

package com.tencent.bkrepo.analyst.event.listener

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.FORBID_STATUS
import com.tencent.bkrepo.common.artifact.constant.FORBID_TYPE
import com.tencent.bkrepo.common.artifact.constant.SCAN_STATUS
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.api.PackageMetadataClient
import com.tencent.bkrepo.repository.pojo.metadata.ForbidType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataSaveRequest
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.model.SubScanTaskDefinition
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.pojo.request.ArtifactPlanRelationRequest
import com.tencent.bkrepo.analyst.service.ScanPlanService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class SubtaskStatusChangedEventListener(
    private val metadataClient: MetadataClient,
    private val packageMetadataClient: PackageMetadataClient,
    private val scanPlanService: ScanPlanService
) {
    @Async
    @EventListener(SubtaskStatusChangedEvent::class)
    fun listen(event: SubtaskStatusChangedEvent) {
        with(event.subtask) {
            recordSubtask(event.subtask)
            // 未指定扫描方案表示为系统级别触发的扫描，不更新元数据
            if (planId == null) {
                return
            }

            // 更新扫描状态元数据
            val metadata = ArrayList<MetadataModel>(4)
            addScanStatus(this, metadata)
            // 更新质量规则元数据
            qualityRedLine?.let {
                // 未通过质量规则，判断是否触发禁用
                if (!qualityRedLine) {
                    addForbidMetadata(this, metadata)
                }
                metadata.add(MetadataModel(key = SubScanTaskDefinition::qualityRedLine.name, value = it, system = true))
            }
            saveMetadata(this, metadata)
            logger.info("update project[$projectId] repo[$repoName] fullPath[$fullPath] metadata[$metadata] success")
        }
    }

    /**
     * 打印子任务详情，用于数据分析
     */
    private fun recordSubtask(subtask: TPlanArtifactLatestSubScanTask) {
        if (SubScanTaskStatus.finishedStatus(subtask.status)) {
            logger.info(subtask.toJsonString().replace(System.lineSeparator(), ""))
        }
    }

    /**
     * 如果方案设置forbidQualityUnPass=true，保存禁用信息
     * 保存metadata(forbidStatus禁用状态(true)、forbidType禁用类型(qualityUnPass))
     */
    fun addForbidMetadata(subTask: TPlanArtifactLatestSubScanTask, metadata: ArrayList<MetadataModel>) {
        with(subTask) {
            // 方案禁用触发设置
            val forbidQualityUnPass = scanQuality?.get(FORBID_QUALITY_UNPASS) as Boolean?
            if (forbidQualityUnPass == true) {
                metadata.add(
                    MetadataModel(
                        key = FORBID_STATUS,
                        value = true,
                        system = true
                    )
                )
                metadata.add(
                    MetadataModel(
                        key = FORBID_TYPE,
                        value = ForbidType.QUALITY_UNPASS.name,
                        system = true
                    )
                )
            }
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
                metadataClient.saveMetadata(request)
            } else {
                val request = PackageMetadataSaveRequest(
                    projectId = projectId,
                    repoName = repoName,
                    packageKey = packageKey!!,
                    version = version!!,
                    versionMetadata = metadata
                )
                packageMetadataClient.saveMetadata(request)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubtaskStatusChangedEventListener::class.java)

        // 禁用质量规则未通过的制品
        const val FORBID_QUALITY_UNPASS = "forbidQualityUnPass"
    }
}
