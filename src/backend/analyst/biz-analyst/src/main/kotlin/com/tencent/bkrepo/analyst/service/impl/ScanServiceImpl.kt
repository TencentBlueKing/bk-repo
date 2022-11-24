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

package com.tencent.bkrepo.analyst.service.impl

import com.alibaba.cola.statemachine.StateMachine
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_BUILD_NUMBER
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_KEY_BID
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_KEY_PID
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_PIPELINE_NAME
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_PLUGIN_NAME
import com.tencent.bkrepo.analyst.pojo.request.PipelineScanRequest
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.analyst.service.ScanPlanService
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.ExecuteSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.FinishSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.PullSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.SubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.CreateTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.ResetTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.StopTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.TaskContext
import com.tencent.bkrepo.analyst.utils.Converter
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Suppress("LongParameterList", "TooManyFunctions")
class ScanServiceImpl @Autowired constructor(
    private val scanTaskDao: ScanTaskDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val scanPlanService: ScanPlanService,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val scannerService: ScannerService,
    private val taskStateMachine: StateMachine<ScanTaskStatus, ScanTaskEvent, TaskContext>,
    private val subtaskStateMachine: StateMachine<SubScanTaskStatus, SubtaskEvent, SubtaskContext>,
) : ScanService {

    override fun scan(scanRequest: ScanRequest, triggerType: ScanTriggerType, userId: String?): ScanTask {
        val context = CreateTaskContext(scanRequest = scanRequest, triggerType = triggerType, userId = userId)
        taskStateMachine.fireEvent(ScanTaskStatus.PENDING, ScanTaskEvent.CREATE, context)
        return context.createdScanTask!!
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun pipelineScan(pipelineScanRequest: PipelineScanRequest): ScanTask {
        with(pipelineScanRequest) {
            val defaultScanPlan = scanPlanService.getOrCreateDefaultPlan(projectId, planType, scanner)
            val metadata = if (pid != null && bid != null) {
                val data = ArrayList<TaskMetadata>()
                pid?.let { data.add(TaskMetadata(key = TASK_METADATA_KEY_PID, value = it)) }
                bid?.let { data.add(TaskMetadata(key = TASK_METADATA_KEY_BID, value = it)) }
                pluginName?.let { data.add(TaskMetadata(key = TASK_METADATA_PLUGIN_NAME, value = it)) }
                buildNo?.let { data.add(TaskMetadata(key = TASK_METADATA_BUILD_NUMBER, value = it)) }
                pipelineName?.let { data.add(TaskMetadata(key = TASK_METADATA_PIPELINE_NAME, value = it)) }
                data
            } else {
                emptyList()
            }
            val context = CreateTaskContext(
                scanRequest = ScanRequest(rule = rule, planId = defaultScanPlan.id!!, metadata = metadata),
                triggerType = ScanTriggerType.PIPELINE,
                userId = SecurityUtils.getUserId(),
                weworkBotUrl = weworkBotUrl,
                chatIds = chatIds
            )
            taskStateMachine.fireEvent(ScanTaskStatus.PENDING, ScanTaskEvent.CREATE, context)
            return context.createdScanTask!!
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopByPlanArtifactLatestSubtaskId(projectId: String, subtaskId: String): Boolean {
        val subtask = planArtifactLatestSubScanTaskDao.find(projectId, subtaskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)
        return subtask.latestSubScanTaskId?.let { stopSubtask(subtask.projectId, it) } ?: false
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopSubtask(projectId: String, subtaskId: String): Boolean {
        val subtask = subScanTaskDao.find(projectId, subtaskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)
        val context = FinishSubtaskContext(
            subtask = subtask,
            targetState = SubScanTaskStatus.STOPPED.name,
            modifiedBy = SecurityUtils.getUserId()
        )
        subtaskStateMachine.fireEvent(SubScanTaskStatus.valueOf(subtask.status), SubtaskEvent.STOP, context)
        return true
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopTask(projectId: String, taskId: String): Boolean {
        val task = scanTaskDao.findByProjectIdAndId(projectId, taskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)

        if (ScanTaskStatus.finishedStatus(task.status)) {
            return false
        }

        val context = StopTaskContext(task)
        taskStateMachine.fireEvent(ScanTaskStatus.valueOf(task.status), ScanTaskEvent.STOP, context)
        return true
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopScanPlan(projectId: String, planId: String): Boolean {
        val unFinishedTasks = scanTaskDao.findUnFinished(projectId, planId)
        unFinishedTasks.forEach {
            stopTask(projectId, it.id!!)
        }
        return true
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun reportResult(reportResultRequest: ReportResultRequest) {
        with(reportResultRequest) {
            val subtask = subScanTaskDao.findById(subTaskId) ?: return
            logger.info("report result, parentTask[${subtask.parentScanTaskId}], subTask[$subTaskId]")
            finishSubtask(subtask = subtask, targetState = scanStatus, scanExecutorResult = scanExecutorResult)
        }
    }

    override fun pull(): SubScanTask? {
        return pullSubScanTask()?.let {
            val parentTask = scanTaskDao.findById(it.parentScanTaskId)!!
            return Converter.convert(it, scannerService.get(it.scanner), parentTask.metadata)
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun updateSubScanTaskStatus(subScanTaskId: String, subScanTaskStatus: String): Boolean {
        val subtask = subScanTaskDao.findById(subScanTaskId)
        if (subtask != null && subScanTaskStatus == SubScanTaskStatus.EXECUTING.name) {
            if (subtask.status != SubScanTaskStatus.EXECUTING.name) {
                val context = ExecuteSubtaskContext(subtask)
                subtaskStateMachine.fireEvent(SubScanTaskStatus.valueOf(subtask.status), SubtaskEvent.EXECUTE, context)
            }
            return true
        }
        return false
    }

    override fun get(subtaskId: String): SubScanTask {
        return subScanTaskDao.findById(subtaskId)?.let {
            Converter.convert(it, scannerService.get(it.scanner))
        } ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, subtaskId)
    }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    @Transactional(rollbackFor = [Throwable::class])
    fun enqueueTimeoutTask() {
        val task = scanTaskDao.timeoutTask(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS) ?: return
        taskStateMachine.fireEvent(ScanTaskStatus.valueOf(task.status), ScanTaskEvent.RESET, ResetTaskContext(task))
    }

    /**
     * 结束处于blocked状态超时的子任务
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun finishBlockTimeoutSubScanTask() {
        subScanTaskDao.blockedTimeoutTasks(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS).records.forEach { subtask ->
            logger.info("subTask[${subtask.id}] of parentTask[${subtask.parentScanTaskId}] block timeout")
            finishSubtask(subtask, SubScanTaskStatus.BLOCK_TIMEOUT.name)
        }
    }

    @Suppress("ReturnCount")
    fun pullSubScanTask(): TSubScanTask? {
        var count = 0
        while (true) {
            // 优先返回待执行任务，再返回超时任务
            val task = subScanTaskDao.firstTaskByStatusIn(listOf(SubScanTaskStatus.CREATED.name))
                ?: subScanTaskDao.firstTimeoutTask(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS)
                ?: return null

            // 处于执行中的任务，而且任务执行了最大允许的次数，直接设置为失败
            if (task.status == SubScanTaskStatus.EXECUTING.name && task.executedTimes >= DEFAULT_MAX_EXECUTE_TIMES) {
                logger.info("subTask[${task.id}] of parentTask[${task.parentScanTaskId}] exceed max execute times")
                finishSubtask(task, SubScanTaskStatus.TIMEOUT.name)
                continue
            }

            val context = PullSubtaskContext(task)
            subtaskStateMachine.fireEvent(SubScanTaskStatus.valueOf(task.status), SubtaskEvent.PULL, context)
            if (context.updated == true) {
                return task
            }

            // 超过最大允许重试次数后说明当前冲突比较严重，有多个扫描器在拉任务，直接返回null
            if (++count >= MAX_RETRY_PULL_TASK_TIMES) {
                return null
            }
        }
    }

    private fun finishSubtask(
        subtask: TSubScanTask,
        targetState: String,
        userId: String? = null,
        scanExecutorResult: ScanExecutorResult? = null
    ) {
        val context = FinishSubtaskContext(
            subtask = subtask,
            targetState = targetState,
            scanExecutorResult = scanExecutorResult,
            modifiedBy = userId
        )
        val event = SubtaskEvent.finishEventOf(targetState)
        subtaskStateMachine.fireEvent(SubScanTaskStatus.valueOf(subtask.status), event, context)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanServiceImpl::class.java)

        /**
         * 默认任务最长执行时间，超过后会触发重试
         */
        private const val DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS = 1200L

        /**
         * 最大允许重复执行次数
         */
        private const val DEFAULT_MAX_EXECUTE_TIMES = 3

        /**
         * 最大允许的拉取任务重试次数
         */
        private const val MAX_RETRY_PULL_TASK_TIMES = 3

        /**
         * 定时扫描超时任务入队
         */
        private const val FIXED_DELAY = 3000L
    }
}
