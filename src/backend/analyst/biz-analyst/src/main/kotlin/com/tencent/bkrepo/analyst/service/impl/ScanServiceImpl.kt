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

import com.tencent.bkrepo.analyst.component.AnalystLoadBalancer
import com.tencent.bkrepo.analyst.component.ReportExporter
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.configuration.ScannerProperties.Companion.DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS
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
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.TaskStateMachineConfiguration.Companion.STATE_MACHINE_ID_SUB_SCAN_TASK
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.ExecuteSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.FinishSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.PullSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.context.CreateTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.ResetTaskContext
import com.tencent.bkrepo.analyst.statemachine.task.context.StopTaskContext
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.EXECUTING
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.PULLED
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.util.concurrent.TimeUnit

@Service
@Suppress("LongParameterList", "TooManyFunctions")
class ScanServiceImpl @Autowired constructor(
    private val scanTaskDao: ScanTaskDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val scanPlanService: ScanPlanService,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val scannerService: ScannerService,
    @Qualifier(STATE_MACHINE_ID_SCAN_TASK)
    private val taskStateMachine: StateMachine,
    @Qualifier(STATE_MACHINE_ID_SUB_SCAN_TASK)
    private val subtaskStateMachine: StateMachine,
    private val redisTemplate: RedisTemplate<String, String>,
    private val reportExporter: ReportExporter,
    private val scannerProperties: ScannerProperties,
) : ScanService {

    override fun scan(scanRequest: ScanRequest, triggerType: ScanTriggerType, userId: String): ScanTask {
        val context = CreateTaskContext(scanRequest = scanRequest, triggerType = triggerType, userId = userId)
        val event = Event(ScanTaskEvent.CREATE.name, context)
        val transitResult = taskStateMachine.sendEvent(ScanTaskStatus.PENDING.name, event)
        return transitResult.result as ScanTask
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
            val event = Event(ScanTaskEvent.CREATE.name, context)
            val transitResult = taskStateMachine.sendEvent(ScanTaskStatus.PENDING.name, event)
            return transitResult.result as ScanTask
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
        val event = Event(SubtaskEvent.STOP.name, context)
        subtaskStateMachine.sendEvent(subtask.status, event)
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
        val event = Event(ScanTaskEvent.STOP.name, context)
        taskStateMachine.sendEvent(task.status, event)
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
            scanExecutorResult?.let { reportExporter.export(subtask, it) }
        }
    }

    override fun pull(dispatcher: String?): SubScanTask? {
        return pullSubScanTask(dispatcher)?.let {
            HttpContextHolder.getRequestOrNull()?.remoteHost?.let { remoteHost ->
                val ops = redisTemplate.opsForValue()
                val key = AnalystLoadBalancer.instanceKey(it.id!!)
                ops.set(key, remoteHost, 1L, TimeUnit.DAYS)
            }
            return SubtaskConverter.convert(it, scannerService.get(it.scanner))
        }
    }

    override fun peek(dispatcher: String?): SubScanTask? {
        val subtask = subScanTaskDao.firstTaskByStatusIn(listOf(SubScanTaskStatus.CREATED.name), dispatcher)
            ?: subScanTaskDao.firstTimeoutTask(scannerProperties.heartbeatTimeout.seconds, dispatcher)
        return subtask?.let { SubtaskConverter.convert(it, scannerService.get(it.scanner)) }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun updateSubScanTaskStatus(subScanTaskId: String, subScanTaskStatus: String): Boolean {
        val subtask = subScanTaskDao.findById(subScanTaskId)
        if (subtask != null && subScanTaskStatus == EXECUTING.name) {
            val context = ExecuteSubtaskContext(subtask)
            val targetState = subtaskStateMachine.sendEvent(subtask.status, Event(SubtaskEvent.EXECUTE.name, context))
            return targetState.transitState == EXECUTING.name
        }
        return false
    }

    override fun heartbeat(subScanTaskId: String) {
        if (subScanTaskDao.heartbeat(subScanTaskId).modifiedCount == 0L) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, subScanTaskId)
        }
    }

    override fun get(subtaskId: String): SubScanTask {
        return subScanTaskDao.findById(subtaskId)?.let {
            SubtaskConverter.convert(it, scannerService.get(it.scanner))
        } ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, subtaskId)
    }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    @Transactional(rollbackFor = [Throwable::class])
    fun enqueueTimeoutTask() {
        val task = scanTaskDao.timeoutTask(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS) ?: return
        taskStateMachine.sendEvent(task.status, Event(ScanTaskEvent.RESET.name, ResetTaskContext(task)))
    }

    /**
     * 结束处于blocked状态超时的子任务
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun finishBlockTimeoutSubScanTask() {
        val blockTimeout = scannerProperties.blockTimeout.seconds
        if (blockTimeout != 0L) {
            subScanTaskDao.blockedTimeoutTasks(blockTimeout).records.forEach { subtask ->
                logger.info("subTask[${subtask.id}] of parentTask[${subtask.parentScanTaskId}] block timeout")
                finishSubtask(subtask, SubScanTaskStatus.BLOCK_TIMEOUT.name)
            }
        }
    }

    @Suppress("ReturnCount")
    fun pullSubScanTask(dispatcher: String?): TSubScanTask? {
        var count = 0
        while (true) {
            // 优先返回待执行任务，再返回超时任务
            val task = subScanTaskDao.firstTaskByStatusIn(listOf(SubScanTaskStatus.CREATED.name), dispatcher)
                ?: subScanTaskDao.firstTimeoutTask(scannerProperties.heartbeatTimeout.seconds, dispatcher)
                ?: return null

            // 处于执行中的任务，而且任务执行了最大允许的次数，直接设置为失败
            val expiredTimestamp =
                Timestamp.valueOf(task.lastModifiedDate).time + scannerProperties.maxTaskDuration.toMillis()
            if (task.executedTimes >= DEFAULT_MAX_EXECUTE_TIMES || System.currentTimeMillis() >= expiredTimestamp) {
                logger.info(
                    "subTask[${task.id}] of parentTask[${task.parentScanTaskId}] " +
                        "exceed max execute times or timeout[${task.lastModifiedDate}]"
                )
                val targetState = if (task.status == EXECUTING.name || task.status == PULLED.name) {
                    SubScanTaskStatus.TIMEOUT.name
                } else {
                    SubScanTaskStatus.FAILED.name
                }
                finishSubtask(task, targetState)
                continue
            }

            val context = PullSubtaskContext(task)
            val transitResult = subtaskStateMachine.sendEvent(task.status, Event(SubtaskEvent.PULL.name, context))
            if (transitResult.result == true) {
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
        subtaskStateMachine.sendEvent(subtask.status, Event(event.name, context))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanServiceImpl::class.java)

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
