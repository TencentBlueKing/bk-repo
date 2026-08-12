/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.agent.constant

/** 设备标识，经 [RunAgentInput.forwardedProps] 传递，写入 RuntimeContext 与 agent_run。 */
const val FORWARDED_PROP_DEVICE_ID = "deviceId"

/** [io.agentscope.core.agent.RuntimeContext] 中存放设备标识的 key，供 Tool / Hook 读取 */
const val RUNTIME_CONTEXT_DEVICE_ID = "deviceId"

/**
 * [io.agentscope.core.agent.RuntimeContext] 中冻结的会话所属项目
 */
const val RUNTIME_CONTEXT_PROJECT_ID = "projectId"

/**
 * [io.agentscope.core.agent.RuntimeContext] 中本轮 HTTP run 的 runId，供消息归档 middleware 使用。
 */
const val RUNTIME_CONTEXT_RUN_ID = "runId"

/**
 * 服务端内部 HTTP 执行尝试号，与 AG-UI canonical [RUNTIME_CONTEXT_RUN_ID] 分离。
 */
const val RUNTIME_CONTEXT_EXECUTION_ID = "executionId"

/** 可选 traceId，经 RunAgentInput.forwardedProps 传递。 */
const val FORWARDED_PROP_TRACE_ID = "traceId"

/** RuntimeContext 中的 traceId 键。 */
const val RUNTIME_CONTEXT_TRACE_ID = "traceId"

/**
 * SSE 发送失败等场景下，middleware 在 [reactor.core.publisher.SignalType.CANCEL] 时仍归档 assistant 片段。
 */
const val RUNTIME_CONTEXT_FORCE_ARCHIVE_ASSISTANT = "forceArchiveAssistant"

/** middleware 内部 per-call 归档状态在 [RuntimeContext] 中的 key */
const val RUNTIME_CONTEXT_MESSAGE_ARCHIVE_STATE = "messageArchiveState"

const val AGENT_THREAD_ID_PREFIX = "s-"

const val AGENT_MESSAGE_ID_PREFIX = "m-"

const val AGENT_RUN_ID_PREFIX = "r-"

const val LOG_OPERATE_SESSION_CREATE = "AGENT_SESSION_CREATE"

const val LOG_OPERATE_SESSION_LIST = "AGENT_SESSION_LIST"

const val LOG_OPERATE_SESSION_MESSAGES = "AGENT_SESSION_MESSAGES"

const val LOG_OPERATE_SESSION_UPDATE = "AGENT_SESSION_UPDATE"

const val LOG_OPERATE_SESSION_DELETE = "AGENT_SESSION_DELETE"

const val LOG_OPERATE_RUN = "AGENT_RUN"

/** Redis 中 threadId -> userId 归属映射的 key 前缀 */
const val AGENT_SESSION_OWNER_KEY_PREFIX = "bkrepo:agent:session-owner:"

/** Redis 中同会话 run 互斥锁 key 前缀 */
const val AGENT_RUN_LOCK_KEY_PREFIX = "bkrepo:agent:run-lock:"

/** Redis 中 thread pending interrupt 快照 key 前缀 */
const val AGENT_PENDING_INTERRUPT_KEY_PREFIX = "bkrepo:agent:pending-interrupt:"

/** Redis 中 resume 幂等指纹 key 前缀 */
const val AGENT_RESUME_IDEMPOTENCY_KEY_PREFIX = "bkrepo:agent:resume-idempotent:"

/** Redis 中 thread 活跃 canonical runId key 前缀 */
const val AGENT_ACTIVE_RUN_KEY_PREFIX = "bkrepo:agent:active-run:"

/** Redis 中 run 取消信号 key 前缀 */
const val AGENT_RUN_CANCEL_KEY_PREFIX = "bkrepo:agent:run-cancel:"

const val LOG_OPERATE_RUN_STATUS = "AGENT_RUN_STATUS"

const val LOG_OPERATE_RUN_STOP = "AGENT_RUN_STOP"

const val LOG_OPERATE_RUN_RECONNECT = "AGENT_RUN_RECONNECT"
