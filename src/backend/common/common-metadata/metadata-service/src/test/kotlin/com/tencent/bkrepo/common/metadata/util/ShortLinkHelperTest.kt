package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkCreateRequest
import com.tencent.bkrepo.common.metadata.properties.ShortLinkProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("短链接工具测试")
class ShortLinkHelperTest {

    private val properties = ShortLinkProperties(
        publicHost = "bkrepo.example.com",
        allowedHosts = mutableListOf("bkrepo.example.com", ".woa.com"),
        defaultTtlDays = 30,
        maxTtlDays = 365,
    )

    @Test
    fun `should accept relative target`() {
        ShortLinkHelper.validateTarget("/web/repository/api/node/detail", properties.allowedHosts)
    }

    @Test
    fun `should reject protocol-relative target`() {
        assertThrows(ErrorCodeException::class.java) {
            ShortLinkHelper.validateTarget("//evil.com/path", properties.allowedHosts)
        }
    }

    @Test
    fun `should accept absolute target in allowlist`() {
        ShortLinkHelper.validateTarget("https://bkrepo.example.com/generic/p/r/f", properties.allowedHosts)
        ShortLinkHelper.validateTarget("https://foo.woa.com/path", properties.allowedHosts)
    }

    @Test
    fun `should reject absolute target not in allowlist`() {
        assertThrows(ErrorCodeException::class.java) {
            ShortLinkHelper.validateTarget("https://evil.com/path", properties.allowedHosts)
        }
    }

    @Test
    fun `should reject absolute target when allowlist empty`() {
        assertThrows(ErrorCodeException::class.java) {
            ShortLinkHelper.validateTarget("https://bkrepo.example.com/path", emptyList())
        }
    }

    @Test
    fun `should use default ttl when expiredDate missing`() {
        val request = ShortLinkCreateRequest(target = "/a", createdBy = "u")
        val expired = ShortLinkHelper.resolveExpiredDate(request, properties)
        val expected = LocalDateTime.now().plusDays(30)
        assertEquals(expected.dayOfYear, expired.dayOfYear)
    }

    @Test
    fun `should reject expiredDate beyond max ttl`() {
        val request = ShortLinkCreateRequest(
            target = "/a",
            createdBy = "u",
            expiredDate = LocalDateTime.now().plusDays(400),
        )
        assertThrows(ErrorCodeException::class.java) {
            ShortLinkHelper.resolveExpiredDate(request, properties)
        }
    }

    @Test
    fun `should build short url with public host`() {
        assertEquals("https://bkrepo.example.com/t/abc12345", ShortLinkHelper.buildShortUrl("abc12345", "bkrepo.example.com"))
        assertEquals("https://bkrepo.example.com/t/abc12345", ShortLinkHelper.buildShortUrl("abc12345", "https://bkrepo.example.com"))
        assertEquals("/t/abc12345", ShortLinkHelper.buildShortUrl("abc12345", ""))
    }

    @Test
    fun `should resolve relative target with scheme and host`() {
        assertEquals(
            "https://bkrepo.example.com/web/repository/api/x",
            ShortLinkHelper.resolveAbsoluteUrl("/web/repository/api/x", "https", "bkrepo.example.com"),
        )
    }

    @Test
    fun `should keep absolute target when resolving`() {
        assertEquals(
            "https://bkrepo.example.com/a",
            ShortLinkHelper.resolveAbsoluteUrl("https://bkrepo.example.com/a", "http", "other.com"),
        )
    }

    @Test
    fun `should generate code with expected length`() {
        assertEquals(ShortLinkHelper.CODE_LENGTH, ShortLinkHelper.generateCode().length)
    }

    @Test
    fun `parameter invalid uses common message code`() {
        val ex = assertThrows(ErrorCodeException::class.java) {
            ShortLinkHelper.validateTarget("", properties.allowedHosts)
        }
        assertEquals(CommonMessageCode.PARAMETER_INVALID, ex.messageCode)
    }
}
