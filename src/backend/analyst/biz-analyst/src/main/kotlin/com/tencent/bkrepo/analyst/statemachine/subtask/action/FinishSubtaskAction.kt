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

package com.tencent.bkrepo.analyst.statemachine.subtask.action

import com.tencent.bkrepo.analyst.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.analyst.component.manager.ScannerConverter
import com.tencent.bkrepo.analyst.component.manager.standard.StandardConverter
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.FileScanResultDao
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.request.filter.MatchFilterRuleRequest
import com.tencent.bkrepo.analyst.service.FilterRuleService
import com.tencent.bkrepo.analyst.service.ScanQualityService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SUB_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.FinishSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.NotifySubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.FinishTaskContext
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanExecutorResult
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.TransitResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Action
@Suppress("LongParameterList")
class FinishSubtaskAction(
    private val scanTaskDao: ScanTaskDao,
    private val scanPlanDao: ScanPlanDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val scannerService: ScannerService,
    private val scanQualityService: ScanQualityService,
    private val filterRuleService: FilterRuleService,
    private val scanExecutorResultManagers: Map<String, ScanExecutorResultManager>,
    private val scannerConverters: Map<String, ScannerConverter>,
    private val scannerMetrics: ScannerMetrics,
    private val publisher: ApplicationEventPublisher
) : SubtaskAction {

    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SUB_SCAN_TASK)
    private lateinit var subtaskStateMachine: StateMachine

    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SCAN_TASK)
    private lateinit var taskStateMachine: StateMachine

    @Transactional(rollbackFor = [Throwable::class])
    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is FinishSubtaskContext)
        with(context) {
            if (targetState != SubScanTaskStatus.SUCCESS.name && targetState != SubScanTaskStatus.STOPPED.name) {
                logger.warn(
                    "task[${subtask.parentScanTaskId}], subtask[${subtask.id}], " +
                            "scan failed: status[$targetState], reason[$reason], result[$scanExecutorResult]"
                )
            }

            val scanner = scannerService.get(subtask.scanner)
            // 对扫描结果去重
            scanExecutorResult?.normalizeResult()
            val overview = scanExecutorResult?.let { overviewOf(subtask, it) } ?: emptyMap()
            // 更新扫描任务结果
            val updateScanTaskResultSuccess = updateScanTaskResult(subtask, targetState, overview)

            if (updateScanTaskResultSuccess && targetState == SubScanTaskStatus.SUCCESS.name) {
                // 扫描成功时更新扫描报告结果
                updateFileScanReport(context, scanner, overview)
            }
            return TransitResult(targetState)
        }
    }

    private fun updateFileScanReport(context: FinishSubtaskContext, scanner: Scanner, overview: Map<String, Any?>) {
        with(context) {
            val subtask = context.subtask
            // 统计任务耗时
            val now = LocalDateTime.now()
            val duration = Duration.between(subtask.startDateTime!!, now)
            scannerMetrics.record(subtask.fullPath, subtask.packageSize, subtask.scanner, duration)

            // 更新文件扫描结果
            fileScanResultDao.upsertResult(
                subtask.credentialsKey,
                subtask.sha256,
                subtask.parentScanTaskId,
                scanner,
                overview,
                subtask.startDateTime,
                now
            )

            // 保存详细扫描结果
            val resultManager = scanExecutorResultManagers[scanner.type]
            resultManager?.save(subtask.credentialsKey, subtask.sha256, scanner, scanExecutorResult!!)
        }
    }

    /**
     * 更新任务状态
     *
     * @return 是否更新成功
     */
    @Suppress("UNCHECKED_CAST")
    @Transactional(rollbackFor = [Throwable::class])
    fun updateScanTaskResult(
        subTask: TSubScanTask,
        resultSubTaskStatus: String,
        overview: Map<String, Any?>,
        modifiedBy: String? = null
    ): Boolean {
        val subTaskId = subTask.id!!
        val parentTaskId = subTask.parentScanTaskId
        // 任务已扫描过，重复上报直接返回
        if (subScanTaskDao.deleteById(subTaskId).deletedCount != 1L) {
            return false
        }
        // 子任务执行结束后唤醒项目另一个子任务
        val context = NotifySubtaskContext(subTask.projectId)
        subtaskStateMachine.sendEvent(SubScanTaskStatus.BLOCKED.name, Event(SubtaskEvent.NOTIFY.name, context))

        // 质量规则检查结果
        val planId = subTask.planId
        val qualityPass = if (subTask.scanQuality.isNullOrEmpty() || planId == null) {
            null
        } else {
            overview.isEmpty() || scanQualityService.checkScanQualityRedLine(planId, overview as Map<String, Number>)
        }
        logger.info("subTask[$subTaskId], qualityPass[$qualityPass]")
        archiveSubScanTaskDao.save(
            SubtaskConverter.convertToArchiveSubtask(
                subTask, resultSubTaskStatus, overview, qualityPass = qualityPass, modifiedBy = modifiedBy
            )
        )
        planArtifactLatestSubScanTaskDao.updateStatus(
            latestSubScanTaskId = subTaskId,
            subtaskScanStatus = resultSubTaskStatus,
            overview = overview,
            modifiedBy = modifiedBy,
            qualityPass = qualityPass
        )
        publisher.publishEvent(
            SubtaskStatusChangedEvent(
                SubScanTaskStatus.valueOf(subTask.status),
                SubtaskConverter.convertToPlanSubtask(
                    subTask, resultSubTaskStatus, overview, qualityPass = qualityPass
                ),
                subTask.metadata.firstOrNull { it.key == TaskMetadata.TASK_METADATA_DISPATCHER }?.value
            )
        )

        scannerMetrics.subtaskStatusChange(
            SubScanTaskStatus.valueOf(subTask.status), SubScanTaskStatus.valueOf(resultSubTaskStatus)
        )
        logger.info("updating scan result, parentTask[$parentTaskId], subTask[$subTaskId][$resultSubTaskStatus]")

        // 更新父任务扫描结果
        val scanSuccess = resultSubTaskStatus == SubScanTaskStatus.SUCCESS.name
        val passCount = if (qualityPass == true) {
            1L
        } else {
            0L
        }
        scanTaskDao.updateScanResult(parentTaskId, 1, overview, scanSuccess, passCount = passCount)
        planId?.let { scanPlanDao.updateScanResultOverview(planId, overview) }
        val finishTaskCtx = FinishTaskContext(parentTaskId, planId)
        val event = Event(ScanTaskEvent.FINISH.name, finishTaskCtx)
        taskStateMachine.sendEvent(ScanTaskStatus.SCANNING_SUBMITTED.name, event)
        return true
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return from != SubScanTaskStatus.NEVER_SCANNED.name && SubScanTaskStatus.finishedStatus(to)
    }

    private fun overviewOf(
        subtask: TSubScanTask,
        result: ScanExecutorResult
    ): Map<String, Any?> {
        val converter = scannerConverters[ScannerConverter.name(result.type)]!!
        return if (converter is StandardConverter && result is StandardScanExecutorResult) {
            val filterRules = filterRuleService.match(
                MatchFilterRuleRequest(
                    projectId = subtask.projectId,
                    repoName = subtask.repoName,
                    planId = subtask.planId,
                    fullPath = subtask.fullPath,
                    packageKey = subtask.packageKey,
                    packageVersion = subtask.version
                )
            )
            converter.convertOverview(
                result.output?.result?.securityResults,
                result.output?.result?.sensitiveResults,
                result.output?.result?.licenseResults,
                filterRules
            )
        } else {
            converter.convertOverview(result)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FinishSubtaskAction::class.java)
    }
}
