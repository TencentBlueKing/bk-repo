/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class AgentRunReplaySinkRegistryTest {

    private val registry = AgentRunReplaySinkRegistry()

    @Test
    fun `late subscriber receives buffered events after cursor`() {
        registry.open("run-1")
        registry.publish("run-1", event(0, """{"type":"RUN_STARTED"}""", false))
        registry.publish("run-1", event(1, """{"type":"TEXT"}""", false))

        val received = CopyOnWriteArrayList<Long>()
        registry.subscribe("run-1", 0L) { received.add(it.eventIndex) }

        registry.publish("run-1", event(2, """{"type":"RUN_FINISHED"}""", true))
        assertEquals(listOf(1L, 2L), received)
    }

    @Test
    fun `subscribe from negative cursor replays all buffered events`() {
        registry.open("run-2")
        registry.publish("run-2", event(0, "a", false))

        val received = CopyOnWriteArrayList<Long>()
        registry.subscribe("run-2", -1L) { received.add(it.eventIndex) }

        assertEquals(listOf(0L), received)
    }

    @Test
    fun `close prevents further publish`() {
        registry.open("run-3")
        registry.publish("run-3", event(0, "a", false))
        registry.close("run-3")

        val received = CopyOnWriteArrayList<Long>()
        assertTrue(registry.subscribe("run-3", -1L) { } == null)
    }

    private fun event(index: Long, json: String, terminal: Boolean): AgentRunReplaySinkRegistry.ReplayEvent {
        return AgentRunReplaySinkRegistry.ReplayEvent(index, json, terminal)
    }
}
