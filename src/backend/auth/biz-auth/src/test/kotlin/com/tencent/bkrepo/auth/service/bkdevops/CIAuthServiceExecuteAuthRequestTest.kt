package com.tencent.bkrepo.auth.service.bkdevops

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.pojo.ApiResponse
import com.tencent.bkrepo.auth.util.HttpUtils
import com.tencent.bkrepo.common.artifact.properties.EnableMultiTenantProperties
import io.micrometer.observation.ObservationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class CIAuthServiceExecuteAuthRequestTest {

    private val userDao: UserDao = mockk(relaxed = true)
    private lateinit var service: CIAuthService
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        mockkObject(HttpUtils)
        val config = DevopsAuthConfig()
        config.setBkciAuthServer("http://localhost")
        config.setBkciAuthToken("token")
        service = CIAuthService(
            config,
            userDao,
            EnableMultiTenantProperties(),
            ObservationRegistry.NOOP
        )
        appender = ListAppender()
        appender.start()
        val logger = LoggerFactory.getLogger(CIAuthService::class.java) as Logger
        logger.addAppender(appender)
        logger.level = Level.INFO
    }

    @AfterEach
    fun tearDown() {
        val logger = LoggerFactory.getLogger(CIAuthService::class.java) as Logger
        logger.detachAppender(appender)
        unmockkObject(HttpUtils)
    }

    @Test
    @DisplayName("devops 4xx 响应降级为空列表且不打 ERROR")
    fun getMemberGroupsInProjectDegradesOnBadRequest() {
        every { HttpUtils.doRequest(any(), any(), any(), any()) } returns ApiResponse(
            400,
            """{"status":400,"message":"bad request"}"""
        )

        val result = service.getMemberGroupsInProject("ut-user", "ut-project")

        assertEquals(emptyList<String>(), result)
        assertFalse(appender.list.any { it.level == Level.ERROR })
        assertTrue(appender.list.any { it.level == Level.WARN })
    }

    @Test
    @DisplayName("devops 2xx 正常反序列化")
    fun getProjectListByUserParsesSuccessBody() {
        every { HttpUtils.doRequest(any(), any(), any(), any()) } returns ApiResponse(
            200,
            """{"status":0,"data":["p1","p2"]}"""
        )

        val result = service.getProjectListByUser("ut-user")

        assertEquals(listOf("p1", "p2"), result)
        assertFalse(appender.list.any { it.level == Level.ERROR })
    }
}
