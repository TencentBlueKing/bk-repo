package com.tencent.bkrepo.common.ratelimiter.service

import jakarta.servlet.http.HttpServletRequest

/**
 * 并发类限流服务接口：在 RateLimiterService.limit() 基础上增加 finish()，
 * 用于请求完成后释放并发槽位（semaphore release + 计数归还）。
 * 仅 connection/concurrent 类服务实现此接口。
 */
interface ConcurrencyLimitService : RateLimiterService {
    fun finish(request: HttpServletRequest)
}
