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

package com.tencent.bkrepo.analyst.statemachine.task.action

import com.tencent.bkrepo.analyst.component.CacheableRepositoryClient
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.FileScanResultDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.exception.TaskSubmitInterruptedException
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.pojo.ProjectScanConfiguration
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.PENDING
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.SCANNING_SUBMITTING
import com.tencent.bkrepo.analyst.service.ProjectScanConfigurationService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SUB_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.iterator.IteratorManager
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.CreateSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.StopTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.SubmitTaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.NEVER_SCANNED
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.TransitResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Action
@Suppress("LongParameterList")
class SubmittingAction(
    private val repositoryClient: CacheableRepositoryClient,
    private val iteratorManager: IteratorManager,
    private val scanTaskDao: ScanTaskDao,
    private val lockOperation: LockOperation,
    private val scannerMetrics: ScannerMetrics,
    private val scannerService: ScannerService,
    private val subScanTaskDao: SubScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val projectScanConfigurationService: ProjectScanConfigurationService,
    private val scannerProperties: ScannerProperties
) : TaskAction {

    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SCAN_TASK)
    private lateinit var taskStateMachine: StateMachine

    @Autowired
    @Lazy
    @Qualifier(STATE_MACHINE_ID_SUB_SCAN_TASK)
    private lateinit var subtaskStateMachine: StateMachine


    override fun support(from: String, to: String, event: String): Boolean {
        return from == PENDING.name && to == SCANNING_SUBMITTING.name && event == ScanTaskEvent.SUBMIT.name
    }

    /**
     * 创建扫描子任务，并提交到扫描队列
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is SubmitTaskContext)
        val scanTask = context.scanTask
        // 设置扫描任务状态为提交子任务中
        val lastModifiedDate = LocalDateTime.parse(scanTask.lastModifiedDateTime, DateTimeFormatter.ISO_DATE_TIME)
        // 更新任务状态失败，表示任务已经被提交过，不再重复处理，直接返回
        if (scanTaskDao.updateStatus(scanTask.taskId, SCANNING_SUBMITTING, lastModifiedDate).modifiedCount == 0L) {
            return TransitResult(source)
        }

        scannerMetrics.incTaskCountAndGet(SCANNING_SUBMITTING)

        // 加锁避免有其他任务正在提交这个project的任务，导致project执行中的任务数量统计错误
        // 此处不要求锁绝对可靠，极端情况下有两个进程同时持有锁时只会导致project执行的任务数量超过配额
        val lockKey = scanTask.scanPlan?.projectId?.let { submitScanTaskLockKey(it) }
        val (submittedSubTaskCount, reuseResultTaskCount) = try {
            if (lockKey == null) {
                submit(scanTask)
            } else {
                lockOperation.doWithLock(lockKey) { submit(scanTask) }
            }
        } catch (e: TaskSubmitInterruptedException) {
            logger.info("task[${e.taskId}] has been stopped")
            return TransitResult(ScanTaskStatus.STOPPING.name)
        } catch (e: Exception) {
            logger.error("submit task[${scanTask.taskId}] failed, try to stop task", e)
            return taskStateMachine.sendEvent(
                SCANNING_SUBMITTING.name,
                Event(ScanTaskEvent.STOP.name, StopTaskContext(scanTaskDao.findById(scanTask.taskId)!!))
            )
        }
        logger.info("submit $submittedSubTaskCount sub tasks, $reuseResultTaskCount sub tasks reuse result")

        // 更新任务状态为所有子任务已提交
        val submittedContext = context.copy(submittedSubTaskCount = submittedSubTaskCount)
        val finishSubmitEvent = Event(ScanTaskEvent.FINISH_SUBMIT.name, submittedContext)
        return taskStateMachine.sendEvent(SCANNING_SUBMITTING.name, finishSubmitEvent)
    }


    /**
     * 遍历[scanTask]指定的所有制品，提交子任务
     *
     * @return first 提交的子任务数量 second 复用扫描结果的子任务数量
     */
    private fun submit(scanTask: ScanTask): Pair<Long, Long> {
        val scanner = scannerService.get(scanTask.scanner)
        val projectId = scanTask.scanPlan?.projectId
        var projectScanConfiguration = projectId?.let {
            projectScanConfigurationService.findProjectOrGlobalScanConfiguration(it)
        }
        logger.info("submitting sub tasks of task[${scanTask.taskId}], scanner: [${scanner.name}]")

        var scanningCount = projectId?.let { subScanTaskDao.scanningCount(it) }
        var submittedSubTaskCount = 0L
        var reuseResultTaskCount = 0L
        val nodeIterator = iteratorManager.createNodeIterator(scanTask, scanner, false)
        for (node in nodeIterator) {
            // 未使用扫描方案的情况直接取node的projectId
            projectScanConfiguration = projectScanConfiguration
                ?: projectScanConfigurationService.findProjectOrGlobalScanConfiguration(node.projectId)
            scanningCount = scanningCount ?: subScanTaskDao.scanningCount(node.projectId)

            val context = CreateSubtaskContext(node, scanner, scanTask)

            // 文件已存在扫描结果，跳过扫描

            val credentialsKey = repositoryClient.get(node.projectId, node.repoName).storageCredentialsKey
            val existsScanResult = fileScanResultDao.find(credentialsKey, node.sha256, scanner.name, scanner.version)

            // 变更子任务状态
            if (existsScanResult != null && !scanTask.force) {
                // 复用扫描结果，变更子任务状态为成功
                logger.info("skip scan file[${node.sha256}], credentials[$credentialsKey]")
                val overview = existsScanResult.scanResult[scanTask.scanner]?.overview ?: emptyMap()
                val createFinishedSubtaskContext = context.copy(existsOverview = overview)
                val event = Event(SubtaskEvent.SUCCESS.name, createFinishedSubtaskContext)
                subtaskStateMachine.sendEvent(NEVER_SCANNED.name, event)
                reuseResultTaskCount++
            } else {
                // 变更子任务状态为CREATED或BLOCKED
                val event = event(scanTask, scanningCount, projectScanConfiguration)
                subtaskStateMachine.sendEvent(NEVER_SCANNED.name, Event(event.name, context))
                // 统计子任务数量
                submittedSubTaskCount++
                if (event == SubtaskEvent.CREATE) {
                    scanningCount++
                }
                // 更新当前正在扫描的任务数
                val updateResult = scanTaskDao.updateScanningCount(scanTask.taskId, 1)
                if (updateResult.modifiedCount == 0L) {
                    // 没有更新表示任务已被停止
                    throw TaskSubmitInterruptedException(scanTask.taskId)
                }
            }

        }

        return Pair(submittedSubTaskCount, reuseResultTaskCount)
    }

    private fun submitScanTaskLockKey(projectId: String) = "scanner:lock:submit:$projectId"

    /**
     * 根据扫描数量是否超过限制返回扫描状态
     *
     * @param scanningCount 正在扫描的任务数量
     * @param projectConfiguration 项目扫描配置
     *
     */
    private fun event(
        scanTask: ScanTask,
        scanningCount: Long,
        projectConfiguration: ProjectScanConfiguration?
    ): SubtaskEvent {
        if (scanTask.isGlobal()) {
            // 全局任务不阻塞
            return SubtaskEvent.CREATE
        }
        val limitSubScanTaskCount = projectConfiguration?.subScanTaskCountLimit
            ?: scannerProperties.defaultProjectSubScanTaskCountLimit
        if (scanningCount >= limitSubScanTaskCount.toLong()) {
            return SubtaskEvent.BLOCK
        }
        return SubtaskEvent.CREATE
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SubmittingAction::class.java)
    }
}
