/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.ratelimiter

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.interceptor.RateLimitHandlerInterceptor
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.UsageRateLimiterService
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(RateLimiterProperties::class)
@ConditionalOnWebApplication
class RateLimiterAutoConfiguration {

    @Bean
    fun rateLimiterMetrics(registry: MeterRegistry): RateLimiterMetrics {
        return RateLimiterMetrics(registry)
    }

    @Bean
    @ConditionalOnProperty(value = ["rate.limiter.enabled"])
    fun urlRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>? = null
    ): UrlRateLimiterService {
        return UrlRateLimiterService(taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate)
    }

    @Bean
    @ConditionalOnProperty(value = ["rate.limiter.enabled"])
    fun usageRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>? = null
    ): UsageRateLimiterService {
        return UsageRateLimiterService(taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate)
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(value = ["rate.limiter.enabled"])
    fun rateLimitHandlerInterceptorRegister(
        urlRateLimiterService: UrlRateLimiterService,
        usageRateLimiterService: UsageRateLimiterService
    ): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(RateLimitHandlerInterceptor(urlRateLimiterService, usageRateLimiterService))
                    .excludePathPatterns("/service/**", "/replica/**")
            }
        }
    }


}
