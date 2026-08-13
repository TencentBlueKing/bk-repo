/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本 JVM 内 active run 的低延迟 replay sink。
 *
 * reconnect 客户端先读 Mongo 已持久化事件，再订阅 sink 消化“查库与订阅之间”的竞态窗口。
 */
@Component
class AgentRunReplaySinkRegistry {

    data class ReplayEvent(
        val eventIndex: Long,
        val eventJson: String,
        val terminal: Boolean,
    )

    private class ReplaySink {
        val events = CopyOnWriteArrayList<ReplayEvent>()
        private val subscribers = CopyOnWriteArrayList<(ReplayEvent) -> Unit>()
        val closed = AtomicBoolean(false)

        fun publish(event: ReplayEvent) {
            if (closed.get()) return
            events.add(event)
            subscribers.forEach { listener ->
                runCatching { listener(event) }
            }
        }

        fun subscribe(fromIndex: Long, listener: (ReplayEvent) -> Unit): () -> Unit {
            events.filter { it.eventIndex > fromIndex }.forEach { listener(it) }
            subscribers.add(listener)
            return { subscribers.remove(listener) }
        }

        fun close() {
            closed.set(true)
            subscribers.clear()
        }
    }

    private val sinks = ConcurrentHashMap<String, ReplaySink>()

    fun open(runId: String) {
        sinks.compute(runId) { _, existing ->
            existing?.close()
            ReplaySink()
        }
    }

    fun publish(runId: String, event: ReplayEvent) {
        sinks[runId]?.publish(event)
    }

    fun subscribe(runId: String, fromIndex: Long, listener: (ReplayEvent) -> Unit): (() -> Unit)? {
        return sinks[runId]?.subscribe(fromIndex, listener)
    }

    fun hasLocalSink(runId: String): Boolean = sinks.containsKey(runId)

    fun close(runId: String) {
        sinks.remove(runId)?.close()
    }
}
