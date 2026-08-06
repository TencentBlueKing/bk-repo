package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.service.ConcurrencyLimitService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 统一并发限流拦截器，按顺序依次调用各 ConcurrencyLimitService。
 * preHandle 中任意服务抛出异常时，逆序回滚已通过的服务；
 * afterCompletion 逆序调用所有服务 finish()，各服务内部已通过 ACTIVE_ATTR 守护幂等。
 */
class ConcurrencyLimitInterceptor(
    private val services: List<ConcurrencyLimitService>
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val passed = ArrayList<ConcurrencyLimitService>(services.size)
        try {
            for (service in services) {
                service.limit(request)
                passed.add(service)
            }
            return true
        } catch (e: Exception) {
            for (i in passed.indices.reversed()) {
                try {
                    passed[i].finish(request)
                } catch (ex: Exception) {
                    logger.warn("finish failed during rollback: ${ex.message}")
                }
            }
            throw e
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        for (i in services.indices.reversed()) {
            try {
                services[i].finish(request)
            } catch (e: Exception) {
                logger.warn("finish failed in afterCompletion: ${e.message}")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConcurrencyLimitInterceptor::class.java)
    }
}
