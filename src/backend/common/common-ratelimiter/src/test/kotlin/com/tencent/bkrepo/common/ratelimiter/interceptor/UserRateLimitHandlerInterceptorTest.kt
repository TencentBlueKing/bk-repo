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
 * UserRateLimitHandlerInterceptor 单元测试
 *
 * 该拦截器是 RequestLimitCheckService.preLimitCheckForUser 的薄封装，
 * 应在用户鉴权拦截器之后运行（order=1）：
 * 核心验证点：
 *   1. preHandle 调用 preLimitCheckForUser 并返回 true
 *   2. preLimitCheckForUser 抛出异常时，异常向上传播
 */
class UserRateLimitHandlerInterceptorTest {

    private val checkService = mock(RequestLimitCheckService::class.java)
    private val interceptor = UserRateLimitHandlerInterceptor(checkService)

    @Test
    fun `preHandle — delegates to preLimitCheckForUser`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        interceptor.preHandle(request, response, Any())

        verify(checkService).preLimitCheckForUser(request)
    }

    @Test
    fun `preHandle — returns true on success`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val result = interceptor.preHandle(request, response, Any())

        Assertions.assertTrue(result)
    }

    @Test
    fun `preHandle — propagates OverloadException from preLimitCheckForUser`() {
        doThrow(OverloadException::class).whenever(checkService).preLimitCheckForUser(any())
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        Assertions.assertThrows(OverloadException::class.java) {
            interceptor.preHandle(request, response, Any())
        }
    }

    @Test
    fun `preHandle — passes the exact request object to the service`() {
        val request = MockHttpServletRequest().apply { requestURI = "/api/user/resource" }
        val response = MockHttpServletResponse()

        interceptor.preHandle(request, response, Any())

        verify(checkService).preLimitCheckForUser(request)
    }
}
