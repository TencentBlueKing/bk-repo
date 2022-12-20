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

package com.tencent.bkrepo.analyst.statemachine

import com.alibaba.cola.statemachine.StateMachine
import com.alibaba.cola.statemachine.builder.StateMachineBuilder
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.FINISHED
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.PENDING
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.SCANNING_SUBMITTED
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.SCANNING_SUBMITTING
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.STOPPED
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.STOPPING
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.BLOCK
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.CREATE
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.DISPATCH_FAILED
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.EXECUTE
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.NOTIFY
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.PULL
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.STOP
import com.tencent.bkrepo.analyst.statemachine.subtask.action.SubtaskAction
import com.tencent.bkrepo.analyst.statemachine.subtask.context.SubtaskContext
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent.FINISH_STOP
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent.FINISH_SUBMIT
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent.RESET
import com.tencent.bkrepo.analyst.statemachine.task.action.TaskAction
import com.tencent.bkrepo.analyst.statemachine.task.context.TaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.BLOCKED
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.BLOCK_TIMEOUT
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.CREATED
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.EXECUTING
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.FAILED
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.NEVER_SCANNED
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.PULLED
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.SUCCESS
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus.TIMEOUT
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 制品分析任务状态定义
 *
 * @param taskActions 任务状态迁移时执行的动作
 * @param subtaskActions 子任务状态迁移时执行的动作
 */
@Configuration
class TaskStateMachineConfiguration(
    private val taskActions: List<TaskAction>,
    private val subtaskActions: List<SubtaskAction>
) {

    @Bean(STATE_MACHINE_ID_SCAN_TASK)
    fun scanTaskStateMachine(): StateMachine<ScanTaskStatus, ScanTaskEvent, TaskContext> {
        return StateMachineBuilderFactory.create<ScanTaskStatus, ScanTaskEvent, TaskContext>().run {
            internalTransition(PENDING, ScanTaskEvent.CREATE, taskActions)
            externalTransition(PENDING, SCANNING_SUBMITTING, ScanTaskEvent.SUBMIT, taskActions)
            externalTransition(SCANNING_SUBMITTING, SCANNING_SUBMITTED, FINISH_SUBMIT, taskActions)
            externalTransition(SCANNING_SUBMITTING, PENDING, RESET, taskActions)
            internalTransition(PENDING, RESET, taskActions)
            // finished state
            externalTransition(SCANNING_SUBMITTED, FINISHED, ScanTaskEvent.FINISH, taskActions)

            val from = arrayOf(PENDING, SCANNING_SUBMITTING, SCANNING_SUBMITTED)
            externalTransitions(from, STOPPING, ScanTaskEvent.STOP, taskActions)
            externalTransition(STOPPING, STOPPED, FINISH_STOP, taskActions)
            build(STATE_MACHINE_ID_SCAN_TASK)
        }
    }

    @Bean(STATE_MACHINE_ID_SUB_SCAN_TASK)
    fun subScanTaskStateMachine(): StateMachine<SubScanTaskStatus, SubtaskEvent, SubtaskContext> {
        return StateMachineBuilderFactory.create<SubScanTaskStatus, SubtaskEvent, SubtaskContext>().run {
            externalTransition(NEVER_SCANNED, CREATED, CREATE, subtaskActions)
            externalTransition(NEVER_SCANNED, BLOCKED, BLOCK, subtaskActions)
            externalTransition(BLOCKED, CREATED, NOTIFY, subtaskActions)
            externalTransitions(arrayOf(CREATED, EXECUTING), PULLED, PULL, subtaskActions)
            internalTransition(PULLED, PULL, subtaskActions)
            externalTransition(PULLED, EXECUTING, EXECUTE, subtaskActions)
            externalTransition(PULLED, CREATED, DISPATCH_FAILED, subtaskActions)

            // finished state
            externalTransition(BLOCKED, BLOCK_TIMEOUT, SubtaskEvent.BLOCK_TIMEOUT, subtaskActions)
            externalTransition(EXECUTING, TIMEOUT, SubtaskEvent.TIMEOUT, subtaskActions)
            externalTransition(EXECUTING, FAILED, SubtaskEvent.FAILED, subtaskActions)
            externalTransition(EXECUTING, SUCCESS, SubtaskEvent.SUCCESS, subtaskActions)
            externalTransition(NEVER_SCANNED, SUCCESS, SubtaskEvent.SUCCESS, subtaskActions)
            val from = arrayOf(BLOCKED, CREATED, PULLED, EXECUTING)
            externalTransitions(from, SubScanTaskStatus.STOPPED, STOP, subtaskActions)
            build(STATE_MACHINE_ID_SUB_SCAN_TASK)
        }
    }

    /**
     * 外部状态流转，任务处于[from]状态时接收到[event]事件将迁移到[to]状态，并执行[actions]中支持该状态流转的动作
     */
    fun <S, E, C> StateMachineBuilder<S, E, C>.externalTransition(
        from: S,
        to: S,
        event: E,
        actions: List<StateAction<S, E, C>>
    ) {
        val on = externalTransition().from(from).to(to).on(event)
        actions.firstOrNull { it.support(from, to, event) }?.let { on.perform(it) }
    }

    /**
     * 外部状态流转，任务处于[from]列表中的任意状态时接收到[event]事件将迁移到[to]状态，
     * 并执行[actions]中支持所有(from, to, event)组合的动作
     */
    @Suppress("SpreadOperator")
    fun <S, E, C> StateMachineBuilder<S, E, C>.externalTransitions(
        from: Array<S>,
        to: S,
        event: E,
        actions: List<StateAction<S, E, C>>
    ) {
        val on = externalTransitions().fromAmong(*from).to(to).on(event)
        actions.firstOrNull { it.support(from, to, event) }?.let { on.perform(it) }
    }

    fun <S, E, C> StateMachineBuilder<S, E, C>.internalTransition(
        state: S,
        event: E,
        actions: List<StateAction<S, E, C>>
    ) {
        val on = internalTransition().within(state).on(event)
        actions.firstOrNull { it.support(state, event) }?.let { on.perform(it) }
    }

    companion object {
        /**
         * 任务状态机id
         */
        const val STATE_MACHINE_ID_SCAN_TASK = "scanTaskStateMachine"

        /**
         * 子任务状态机id
         */
        const val STATE_MACHINE_ID_SUB_SCAN_TASK = "subScanTaskStateMachine"
    }
}
