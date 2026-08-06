package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * NonUserRateLimitHandlerInterceptor 单元测试
 *
 * 该拦截器是 RequestLimitCheckService.preLimitCheckForNonUser 的薄封装：
 * 核心验证点：
 *   1. preHandle 调用 preLimitCheckForNonUser 并返回 true
 *   2. preLimitCheckForNonUser 抛出异常时，异常向上传播（不吞掉）
 */
class NonUserRateLimitHandlerInterceptorTest {

    private val checkService = mock(RequestLimitCheckService::class.java)
    private val interceptor = NonUserRateLimitHandlerInterceptor(checkService)

    @Test
    fun `preHandle — delegates to preLimitCheckForNonUser`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        interceptor.preHandle(request, response, Any())

        verify(checkService).preLimitCheckForNonUser(request)
    }

    @Test
    fun `preHandle — returns true on success`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val result = interceptor.preHandle(request, response, Any())

        Assertions.assertTrue(result)
    }

    @Test
    fun `preHandle — propagates OverloadException from preLimitCheckForNonUser`() {
        doThrow(OverloadException::class).whenever(checkService).preLimitCheckForNonUser(any())
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        Assertions.assertThrows(OverloadException::class.java) {
            interceptor.preHandle(request, response, Any())
        }
    }

    @Test
    fun `preHandle — passes the exact request object to the service`() {
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()

        interceptor.preHandle(request, response, Any())

        verify(checkService).preLimitCheckForNonUser(request)
    }
}
