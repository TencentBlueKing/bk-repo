package com.tencent.bkrepo.common.metadata.service.shortlink.impl

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.dao.shortlink.ShortLinkDao
import com.tencent.bkrepo.common.metadata.model.TShortLink
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkListOption
import com.tencent.bkrepo.common.metadata.properties.ShortLinkProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@DisplayName("短链接服务测试")
class ShortLinkServiceImplTest {

    private lateinit var shortLinkDao: ShortLinkDao
    private lateinit var service: ShortLinkServiceImpl
    private val properties = ShortLinkProperties(
        publicHost = "bkrepo.example.com",
        allowedHosts = mutableListOf("bkrepo.example.com"),
        defaultTtlDays = 30,
        maxTtlDays = 365,
    )

    @BeforeEach
    fun setUp() {
        shortLinkDao = mock()
        service = ShortLinkServiceImpl(shortLinkDao, properties)
    }

    @Test
    fun `should create short link`() {
        whenever(shortLinkDao.insert(any<TShortLink>())).thenAnswer { it.arguments[0] }

        val result = service.create(
            ShortLinkCreateRequest(target = "/web/repository/api/x", createdBy = "user"),
        )

        assertEquals(8, result.code.length)
        assertEquals("/web/repository/api/x", result.target)
        assertEquals("https://bkrepo.example.com/t/${result.code}", result.shortUrl)
        verify(shortLinkDao).insert(any<TShortLink>())
    }

    @Test
    fun `should resolve relative target`() {
        whenever(shortLinkDao.findByCode("abc12345")).thenReturn(
            TShortLink(
                code = "abc12345",
                target = "/web/repository/api/x",
                expiredDate = LocalDateTime.now().plusDays(1),
                createdBy = "user",
                createdDate = LocalDateTime.now(),
                lastModifiedBy = "user",
                lastModifiedDate = LocalDateTime.now(),
            ),
        )

        val url = service.resolve("abc12345", "https", "bkrepo.example.com")
        assertEquals("https://bkrepo.example.com/web/repository/api/x", url)
    }

    @Test
    fun `should throw not found when code missing`() {
        whenever(shortLinkDao.findByCode("missing")).thenReturn(null)
        assertThrows(NotFoundException::class.java) {
            service.resolve("missing", "https", "bkrepo.example.com")
        }
    }

    @Test
    fun `should throw gone when expired`() {
        whenever(shortLinkDao.findByCode("expired1")).thenReturn(
            TShortLink(
                code = "expired1",
                target = "/a",
                expiredDate = LocalDateTime.now().minusDays(1),
                createdBy = "user",
                createdDate = LocalDateTime.now().minusDays(2),
                lastModifiedBy = "user",
                lastModifiedDate = LocalDateTime.now().minusDays(2),
            ),
        )

        val ex = assertThrows(ErrorCodeException::class.java) {
            service.resolve("expired1", "https", "bkrepo.example.com")
        }
        assertEquals(HttpStatus.GONE, ex.status)
        assertEquals(CommonMessageCode.RESOURCE_EXPIRED, ex.messageCode)
    }

    @Test
    fun `get should return expired record`() {
        whenever(shortLinkDao.findByCode("expired1")).thenReturn(
            TShortLink(
                code = "expired1",
                target = "/a",
                expiredDate = LocalDateTime.now().minusDays(1),
                createdBy = "user",
                createdDate = LocalDateTime.now().minusDays(2),
                lastModifiedBy = "user",
                lastModifiedDate = LocalDateTime.now().minusDays(2),
            ),
        )

        val result = service.get("expired1")
        assertEquals("expired1", result?.code)
    }

    @Test
    fun `delete should throw when missing`() {
        whenever(shortLinkDao.deleteByCode("missing")).thenReturn(false)
        assertThrows(NotFoundException::class.java) {
            service.delete("missing")
        }
    }

    @Test
    fun `listByCreator should query by createdBy`() {
        whenever(shortLinkDao.count(any())).thenReturn(0)
        whenever(shortLinkDao.find(any())).thenReturn(emptyList())

        val page = service.listByCreator(ShortLinkListOption(createdBy = "user", pageNumber = 1, pageSize = 20))
        assertEquals(0, page.totalRecords)
        assertEquals(0, page.records.size)
    }
}
