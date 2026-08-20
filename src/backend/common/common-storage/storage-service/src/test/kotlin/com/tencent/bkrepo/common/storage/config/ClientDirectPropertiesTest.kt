package com.tencent.bkrepo.common.storage.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClientDirectPropertiesTest {

    @Test
    fun `all matches any repo`() {
        val props = ClientDirectProperties().apply { all = true }
        assertTrue(props.matches("p1", "r1"))
    }

    @Test
    fun `project whitelist`() {
        val props = ClientDirectProperties().apply { projects = setOf("p1") }
        assertTrue(props.matches("p1", "r1"))
        assertFalse(props.matches("p2", "r1"))
    }

    @Test
    fun `repo whitelist`() {
        val props = ClientDirectProperties().apply { repos = setOf("p1/r1") }
        assertTrue(props.matches("p1", "r1"))
        assertFalse(props.matches("p1", "r2"))
    }

    @Test
    fun `empty matches nothing`() {
        assertFalse(ClientDirectProperties().matches("p1", "r1"))
    }
}
