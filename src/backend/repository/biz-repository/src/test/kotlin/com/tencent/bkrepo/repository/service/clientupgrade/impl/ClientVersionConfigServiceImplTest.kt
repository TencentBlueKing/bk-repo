package com.tencent.bkrepo.repository.service.clientupgrade.impl

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientVersionConfigServiceImplTest {

    private val service = ClientVersionConfigServiceImpl(
        clientVersionConfigDao = mockk(),
        clientVersionConfigCache = mockk(),
    )

    @Test
    fun `should compare major minor and patch numerically`() {
        assertVersionGreater("3.0.0", "2.0.1")
        assertVersionGreater("2.0.10", "1.0.2")
        assertVersionGreater("1.0.10", "1.0.1")
        assertVersionEquals("3.0.1", "3.0.1")
    }

    @Test
    fun `should treat release version as greater than pre release`() {
        assertVersionGreater("3.0.1", "3.0.1-gray.20")
        assertVersionGreater("v3.0.1", "3.0.1-gray.20")
    }

    @Test
    fun `should compare pre release numeric identifiers numerically`() {
        assertVersionGreater("3.0.1-gray.20", "3.0.1-gray.2")
        assertVersionGreater("3.0.1-gray.10", "3.0.1-gray.2")
        assertVersionGreater("3.0.1-gray.002", "3.0.1-gray.1")
    }

    @Test
    fun `should compare pre release identifier count when prefix is equal`() {
        assertVersionGreater("3.0.1-gray.2.1", "3.0.1-gray.2")
        assertVersionGreater("3.0.1-gray.2.alpha", "3.0.1-gray.2")
    }

    @Test
    fun `should support legacy dot style pre release suffix`() {
        assertVersionGreater("3.0.1", "3.0.1.gray20")
        assertVersionGreater("3.0.1.gray20", "3.0.1.gray2")
    }

    @Test
    fun `should keep string compare fallback for invalid versions`() {
        assertTrue(compareVersion("custom-build-b", "custom-build-a") > 0)
        assertEquals(0, compareVersion("custom-build", "custom-build"))
    }

    private fun assertVersionGreater(left: String, right: String) {
        assertTrue(compareVersion(left, right) > 0, "$left should be greater than $right")
        assertTrue(compareVersion(right, left) < 0, "$right should be less than $left")
    }

    private fun assertVersionEquals(left: String, right: String) {
        assertEquals(0, compareVersion(left, right))
        assertEquals(0, compareVersion(right, left))
    }

    private fun compareVersion(left: String, right: String): Int {
        val method = service.javaClass.getDeclaredMethod(
            "compareVersion",
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(service, left, right) as Int
    }
}
