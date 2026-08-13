/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime.store

/** Active run 分布式状态：run 锁、active runId、跨副本 stop 信号。 */
interface ActiveRunStateStore {

    fun tryAcquireLock(userId: String, threadId: String): Boolean

    fun releaseLock(userId: String, threadId: String)

    fun isLockHeld(userId: String, threadId: String): Boolean

    fun bindActiveRun(userId: String, threadId: String, runId: String)

    fun getActiveRunId(userId: String, threadId: String): String?

    fun clearActiveRun(userId: String, threadId: String)

    fun requestStop(runId: String)

    fun isStopRequested(runId: String): Boolean

    fun clearStop(runId: String)
}
