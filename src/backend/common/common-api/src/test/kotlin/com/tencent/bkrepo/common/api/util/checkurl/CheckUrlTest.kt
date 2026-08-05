package com.tencent.bkrepo.common.api.util.checkurl

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.MalformedURLException

class CheckUrlTest {

    @Test
    fun `accepts https url with default subhost rules`() {
        val config = UrlCheckConfig(
            schemes = listOf("https"),
            rules = listOf("example.com"),
            mode = "subhost",
        )
        assertDoesNotThrow { CheckUrl.checkUrl("https://repo.example.com", config) }
    }

    @Test
    fun `rejects invalid scheme`() {
        val config = UrlCheckConfig(
            schemes = listOf("https"),
            rules = listOf(".*"),
            mode = "regex",
        )
        assertThrows(MalformedURLException::class.java) {
            CheckUrl.checkUrl("http://repo.example.com", config)
        }
    }
}
