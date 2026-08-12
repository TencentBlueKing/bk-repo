package com.tencent.bkrepo.maven.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

internal class MavenActiveContentHeadersTest {

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `should set headers for html`() {
        val response = bindResponse()
        MavenActiveContentHeaders.applyIfActiveContent("demo-1.0.0.html")
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
        assertEquals(true, response.getHeader("Content-Security-Policy")?.contains("script-src 'none'"))
    }

    @Test
    fun `should skip jar`() {
        val response = bindResponse()
        MavenActiveContentHeaders.applyIfActiveContent("demo-1.0.0.jar")
        assertNull(response.getHeader("X-Content-Type-Options"))
        assertNull(response.getHeader("Content-Security-Policy"))
    }

    private fun bindResponse(): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        RequestContextHolder.setRequestAttributes(
            ServletRequestAttributes(MockHttpServletRequest(), response)
        )
        return response
    }
}
