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

package com.tencent.bkrepo.analyst.statemachine.subtask.action

import com.tencent.bkrepo.analyst.component.CacheableRepositoryClient
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.model.TArchiveSubScanTask
import com.tencent.bkrepo.analyst.service.ScanQualityService
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.CreateSubtaskContext
import com.tencent.bkrepo.analyst.utils.Converter
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.TransitResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Action
@Suppress("LongParameterList")
class ReuseResultAction(
    private val cacheableRepositoryClient: CacheableRepositoryClient,
    private val scanQualityService: ScanQualityService,
    private val scannerMetrics: ScannerMetrics,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val scanTaskDao: ScanTaskDao,
    private val scanPlanDao: ScanPlanDao,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val publisher: ApplicationEventPublisher
) : SubtaskAction {
    @Autowired
    @Lazy
    private lateinit var self: ReuseResultAction

    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is CreateSubtaskContext)
        val overview = context.existsOverview?.let { Converter.convert(it) }
        val finishedSubtask = createReuseResultSubtask(context, overview)
        // 更新当前正在扫描的任务数
        val passCount = if (finishedSubtask.qualityRedLine == true) 1L else 0L
        self.saveReuseResultSubtask(listOf(finishedSubtask))
        scanTaskDao.updateScanResult(
            context.scanTask.taskId,
            1,
            context.existsOverview ?: emptyMap(),
            success = true,
            reuseResult = true,
            passCount = passCount
        )
        // 更新扫描方案预览数据
        val planId = context.scanTask.scanPlan?.id
        if (planId != null && context.existsOverview?.isNotEmpty() == true) {
            scanPlanDao.updateScanResultOverview(planId, context.existsOverview)
        }
        scannerMetrics.incReuseResultSubtaskCount()
        return TransitResult(target)
    }

    private fun createReuseResultSubtask(
        context: CreateSubtaskContext,
        overview: Map<String, Number>?
    ): TArchiveSubScanTask {
        val scanTask = context.scanTask
        val node = context.node
        val qualityRule = context.scanTask.scanPlan?.scanQuality
        with(node) {
            val now = LocalDateTime.now()
            val repoInfo = cacheableRepositoryClient.get(projectId, repoName)
            // 质量检查结果
            val qualityPass = if (!qualityRule.isNullOrEmpty()) {
                scanQualityService.checkScanQualityRedLine(qualityRule, overview ?: emptyMap(), context.scanner)
            } else {
                null
            }
            return TArchiveSubScanTask(
                createdBy = scanTask.createdBy,
                createdDate = now,
                lastModifiedBy = scanTask.createdBy,
                lastModifiedDate = now,
                startDateTime = now,
                finishedDateTime = now,

                triggerType = scanTask.triggerType,
                parentScanTaskId = scanTask.taskId,
                planId = scanTask.scanPlan?.id,

                projectId = projectId,
                repoName = repoName,
                repoType = repoInfo.type.name,
                packageKey = packageKey,
                version = packageVersion,
                fullPath = fullPath,
                artifactName = artifactName,

                status = SubScanTaskStatus.SUCCESS.name,
                executedTimes = 0,
                scanner = scanTask.scanner,
                scannerType = scanTask.scannerType,
                sha256 = sha256,
                size = size,
                packageSize = packageSize,
                credentialsKey = repoInfo.storageCredentialsKey,

                scanResultOverview = overview,
                qualityRedLine = qualityPass,
                scanQuality = scanTask.scanPlan?.scanQuality
            )
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun saveReuseResultSubtask(finishedSubtasks: Collection<TArchiveSubScanTask>) {
        if (finishedSubtasks.isEmpty()) {
            return
        }
        val tasks = archiveSubScanTaskDao.insert(finishedSubtasks)
        val planArtifactLatestSubtasks = tasks.map {
            SubtaskConverter.convertToPlanSubtask(it, it.status)
        }
        planArtifactLatestSubScanTaskDao.replace(planArtifactLatestSubtasks)
        planArtifactLatestSubtasks.forEach { publisher.publishEvent(SubtaskStatusChangedEvent(null, it)) }
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return from == SubScanTaskStatus.NEVER_SCANNED.name && event == SubtaskEvent.SUCCESS.name
    }
}
