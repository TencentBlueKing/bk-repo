package com.tencent.bkrepo.generic.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.IOException
import java.util.Locale

@DisplayName("IOExceptionHandler Jackson 反序列化异常映射")
class IOExceptionHandlerTest {

    private val handler = IOExceptionHandler()
    private lateinit var servletResponse: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        val messageSource = StaticMessageSource()
        messageSource.addMessage(
            CommonMessageCode.REQUEST_CONTENT_INVALID.getKey(),
            Locale.getDefault(),
            "invalid content"
        )
        messageSource.addMessage(
            CommonMessageCode.SYSTEM_ERROR.getKey(),
            Locale.getDefault(),
            "system error"
        )
        val context = GenericApplicationContext()
        context.beanFactory.registerSingleton("messageSource", messageSource)
        context.refresh()
        SpringContextUtils().setApplicationContext(context)

        val request = MockHttpServletRequest()
        servletResponse = MockHttpServletResponse()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request, servletResponse))
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `illegal enum value maps to 400 not 500`() {
        val result = handler.handler(invalidEnumException())
        assertEquals(CommonMessageCode.REQUEST_CONTENT_INVALID.getCode(), result.code)
        assertEquals(HttpStatus.BAD_REQUEST.value, servletResponse.status)
    }

    @Test
    fun `generic IOException still maps to system error`() {
        val result = handler.handler(IOException("disk full"))
        assertEquals(CommonMessageCode.SYSTEM_ERROR.getCode(), result.code)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value, servletResponse.status)
    }

    private fun invalidEnumException(): InvalidFormatException {
        return try {
            ObjectMapper().readValue("\"PREVIEWs\"", TokenType::class.java)
            error("expected InvalidFormatException")
        } catch (exception: InvalidFormatException) {
            exception
        }
    }
}
