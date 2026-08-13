/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime.store

import java.util.concurrent.ConcurrentHashMap

class InMemoryActiveRunStateStore : ActiveRunStateStore {

    private val locks = ConcurrentHashMap<String, String>()
    private val activeRuns = ConcurrentHashMap<String, String>()
    private val stopRequested = ConcurrentHashMap.newKeySet<String>()

    override fun tryAcquireLock(userId: String, threadId: String): Boolean {
        val key = scopeKey(userId, threadId)
        return locks.putIfAbsent(key, key) == null
    }

    override fun releaseLock(userId: String, threadId: String) {
        locks.remove(scopeKey(userId, threadId))
    }

    override fun isLockHeld(userId: String, threadId: String): Boolean {
        return locks.containsKey(scopeKey(userId, threadId))
    }

    override fun bindActiveRun(userId: String, threadId: String, runId: String) {
        activeRuns[scopeKey(userId, threadId)] = runId
    }

    override fun getActiveRunId(userId: String, threadId: String): String? {
        return activeRuns[scopeKey(userId, threadId)]
    }

    override fun clearActiveRun(userId: String, threadId: String) {
        activeRuns.remove(scopeKey(userId, threadId))
    }

    override fun requestStop(runId: String) {
        stopRequested.add(runId)
    }

    override fun isStopRequested(runId: String): Boolean = stopRequested.contains(runId)

    override fun clearStop(runId: String) {
        stopRequested.remove(runId)
    }

    private fun scopeKey(userId: String, threadId: String): String = "$userId:$threadId"
}
