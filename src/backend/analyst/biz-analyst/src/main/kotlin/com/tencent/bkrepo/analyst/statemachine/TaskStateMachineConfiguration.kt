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

import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.FINISHED
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.PENDING
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.SCANNING_SUBMITTED
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.SCANNING_SUBMITTING
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.STOPPED
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus.STOPPING
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.BLOCK
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.CREATE
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.EXECUTE
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.NOTIFY
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.PULL
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.STOP
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent.RETRY
import com.tencent.bkrepo.analyst.statemachine.subtask.action.SubtaskAction
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent.FINISH_STOP
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent.FINISH_SUBMIT
import com.tencent.bkrepo.analyst.statemachine.task.ScanTaskEvent.RESET
import com.tencent.bkrepo.analyst.statemachine.task.action.TaskAction
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
import com.tencent.bkrepo.statemachine.StateMachine
import com.tencent.bkrepo.statemachine.builder.StateMachineBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 制品分析任务状态定义
 *
 * @param taskActions 任务状态迁移时执行的动作
 * @param subtaskActions 子任务状态迁移时执行的动作
 */
@Configuration(proxyBeanMethods = false)
class TaskStateMachineConfiguration(
    private val taskActions: List<TaskAction>,
    private val subtaskActions: List<SubtaskAction>
) {

    @Bean(STATE_MACHINE_ID_SCAN_TASK)
    fun scanTaskStateMachine(): StateMachine {
        return StateMachineBuilder.stateMachine(STATE_MACHINE_ID_SCAN_TASK) {
            transition(PENDING, PENDING, ScanTaskEvent.CREATE, taskActions)
            transition(PENDING, SCANNING_SUBMITTING, ScanTaskEvent.SUBMIT, taskActions)
            transition(SCANNING_SUBMITTING, SCANNING_SUBMITTED, FINISH_SUBMIT, taskActions)
            transition(SCANNING_SUBMITTING, PENDING, RESET, taskActions)
            transition(PENDING, PENDING, RESET, taskActions)
            // finished state
            transition(SCANNING_SUBMITTED, FINISHED, ScanTaskEvent.FINISH, taskActions)

            val from = arrayOf(PENDING, SCANNING_SUBMITTING, SCANNING_SUBMITTED)
            transitions(from, STOPPING, ScanTaskEvent.STOP, taskActions)
            transition(STOPPING, STOPPED, FINISH_STOP, taskActions)
        }
    }

    @Bean(STATE_MACHINE_ID_SUB_SCAN_TASK)
    fun subScanTaskStateMachine(): StateMachine {
        return StateMachineBuilder.stateMachine(STATE_MACHINE_ID_SUB_SCAN_TASK) {
            transition(NEVER_SCANNED, CREATED, CREATE, subtaskActions)
            transition(NEVER_SCANNED, BLOCKED, BLOCK, subtaskActions)
            transition(BLOCKED, CREATED, NOTIFY, subtaskActions)
            // 通过dispatcher多次分发失败时，可能从CREATED状态转移到FAILED状态
            transition(CREATED, FAILED, SubtaskEvent.FAILED, subtaskActions)
            transitions(arrayOf(CREATED, EXECUTING), PULLED, PULL, subtaskActions)
            transition(PULLED, PULLED, PULL, subtaskActions)
            transition(PULLED, EXECUTING, EXECUTE, subtaskActions)
            transition(PULLED, CREATED, RETRY, subtaskActions)
            // 超过最长允许执行的时间时，可能会从PULLED转移到FAILED状态
            transition(PULLED, FAILED, SubtaskEvent.FAILED, subtaskActions)
            transition(PULLED, TIMEOUT, SubtaskEvent.TIMEOUT, subtaskActions)

            // finished state
            transition(BLOCKED, BLOCK_TIMEOUT, SubtaskEvent.BLOCK_TIMEOUT, subtaskActions)
            transition(EXECUTING, TIMEOUT, SubtaskEvent.TIMEOUT, subtaskActions)
            transition(EXECUTING, FAILED, SubtaskEvent.FAILED, subtaskActions)
            transition(EXECUTING, SUCCESS, SubtaskEvent.SUCCESS, subtaskActions)
            transition(EXECUTING, CREATED, RETRY, subtaskActions)
            transition(NEVER_SCANNED, SUCCESS, SubtaskEvent.SUCCESS, subtaskActions)
            val from = arrayOf(BLOCKED, CREATED, PULLED, EXECUTING)
            transitions(from, SubScanTaskStatus.STOPPED, STOP, subtaskActions)
        }
    }

    /**
     * 外部状态流转，任务处于[from]状态时接收到[event]事件将迁移到[to]状态，并执行[actions]中支持该状态流转的动作
     */
    private fun <S, E> StateMachineBuilder.transition(from: S, to: S, event: E, actions: List<StateAction>) {
        addTransition {
            source(from.toString())
            target(to.toString())
            event(event.toString())
            actions.firstOrNull { it.support(from.toString(), to.toString(), event.toString()) }?.let { action(it) }
        }
    }

    /**
     * 外部状态流转，任务处于[from]列表中的任意状态时接收到[event]事件将迁移到[to]状态，
     * 并执行[actions]中支持所有(from, to, event)组合的动作
     */
    @Suppress("SpreadOperator")
    private fun <S, E> StateMachineBuilder.transitions(from: Array<S>, to: S, event: E, actions: List<StateAction>) {
        val fromStr = from.map { it.toString() }.toTypedArray()
        addTransition {
            sources(*fromStr)
            target(to.toString())
            event(event.toString())
            actions.firstOrNull { it.support(fromStr, to.toString(), event.toString()) }?.let { action(it) }
        }
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
