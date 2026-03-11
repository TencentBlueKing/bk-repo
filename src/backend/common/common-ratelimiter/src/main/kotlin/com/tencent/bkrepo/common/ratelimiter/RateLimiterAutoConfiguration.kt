/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.common.ratelimiter.interceptor.NonUserRateLimitHandlerInterceptor
import com.tencent.bkrepo.common.ratelimiter.interceptor.UserRateLimitHandlerInterceptor
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.DownloadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.UploadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRepoRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.user.UserUrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.user.UserUrlRepoRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.DownloadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.UploadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.user.UserDownloadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.user.UserUploadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ConditionalOnWebApplication
@Import(
    RateLimitRepository::class,
    RateLimiterProperties::class
)
class RateLimiterAutoConfiguration {

    @Bean
    fun rateLimitService(rateLimitRepository: RateLimitRepository): RateLimiterConfigService {
        return RateLimiterConfigService(rateLimitRepository)
    }

    @Bean
    fun rateLimiterMetrics(registry: MeterRegistry): RateLimiterMetrics {
        return RateLimiterMetrics(registry)
    }

    @Bean(URL_REPO_RATELIMITER_SERVICE)
    fun urlRepoRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UrlRepoRateLimiterService {
        return UrlRepoRateLimiterService(
            taskScheduler,
            rateLimiterProperties,
            rateLimiterMetrics,
            redisTemplate,
            rateLimiterConfigService
        )
    }

    @Bean(URL_RATELIMITER_SERVICE)
    fun urlRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UrlRateLimiterService {
        return UrlRateLimiterService(
            taskScheduler,
            rateLimiterProperties,
            rateLimiterMetrics,
            redisTemplate,
            rateLimiterConfigService
        )
    }

    @Bean(USER_URL_REPO_RATELIMITER_SERVICE)
    fun userUrlRepoRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UserUrlRepoRateLimiterService {
        return UserUrlRepoRateLimiterService(
            taskScheduler,
            rateLimiterProperties,
            rateLimiterMetrics,
            redisTemplate,
            rateLimiterConfigService
        )
    }

    @Bean(UPLOAD_USAGE_RATELIMITER_SERVICE)
    fun uploadUsageRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UploadUsageRateLimiterService {
        return UploadUsageRateLimiterService(
            taskScheduler,
            rateLimiterProperties,
            rateLimiterMetrics,
            redisTemplate,
            rateLimiterConfigService
        )
    }

    @Bean(DOWNLOAD_USAGE_RATELIMITER_SERVICE)
    fun downloadUsageRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): DownloadUsageRateLimiterService {
        return DownloadUsageRateLimiterService(
            taskScheduler,
            rateLimiterProperties,
            rateLimiterMetrics,
            redisTemplate,
            rateLimiterConfigService
        )
    }

    @Bean(USER_DOWNLOAD_USAGE_RATELIMITER_SERVICE)
    fun userDownloadUsageRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UserDownloadUsageRateLimiterService {
        return UserDownloadUsageRateLimiterService(
            taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
    }

    @Bean(USER_UPLOAD_USAGE_RATELIMITER_SERVICE)
    fun userUploadUsageRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UserUploadUsageRateLimiterService {
        return UserUploadUsageRateLimiterService(
            taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
    }

    @Bean(USER_URL_RATELIMITER_SERVICE)
    fun userUrlRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UserUrlRateLimiterService {
        return UserUrlRateLimiterService(
            taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
    }

    @Bean(UPLOAD_BANDWIDTH_RATELIMITER_ERVICE)
    fun uploadBandwidthRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): UploadBandwidthRateLimiterService {
        return UploadBandwidthRateLimiterService(
            taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
    }

    @Bean(DOWNLOAD_BANDWIDTH_RATELIMITER_SERVICE)
    fun downloadBandwidthRateLimiterService(
        taskScheduler: ThreadPoolTaskScheduler,
        rateLimiterProperties: RateLimiterProperties,
        rateLimiterMetrics: RateLimiterMetrics,
        redisTemplate: RedisTemplate<String, String>,
        rateLimiterConfigService: RateLimiterConfigService
    ): DownloadBandwidthRateLimiterService {
        return DownloadBandwidthRateLimiterService(
            taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
    }

    @Bean
    fun requestLimitCheckService(
        rateLimiterProperties: RateLimiterProperties,
    ): RequestLimitCheckService {
        return RequestLimitCheckService(rateLimiterProperties)
    }

    @Bean
    @ConditionalOnWebApplication
    fun rateLimitHandlerInterceptorRegister(
        requestLimitCheckService: RequestLimitCheckService
    ): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                // 不需要用户校验的限流拦截器，应在HttpAuthInterceptor之前执行
                registry.addInterceptor(
                    NonUserRateLimitHandlerInterceptor(
                        requestLimitCheckService = requestLimitCheckService
                    )
                )
                    .excludePathPatterns("/service/**", "/replica/**")
                    .order(Ordered.HIGHEST_PRECEDENCE)
                
                // 需要用户校验的限流拦截器，应在HttpAuthInterceptor之后执行
                // HttpAuthInterceptor默认order为0，所以用户态拦截器使用1
                registry.addInterceptor(
                    UserRateLimitHandlerInterceptor(
                        requestLimitCheckService = requestLimitCheckService
                    )
                )
                    .excludePathPatterns("/service/**", "/replica/**")
                    .order(1)
                
                super.addInterceptors(registry)
            }
        }
    }


    companion object {
        const val DOWNLOAD_BANDWIDTH_RATELIMITER_SERVICE = "downloadBandwidthRateLimiterService"
        const val UPLOAD_BANDWIDTH_RATELIMITER_ERVICE = "uploadBandwidthRateLimiterService"
        const val USER_URL_RATELIMITER_SERVICE = "userUrlRateLimiterService"
        const val USER_UPLOAD_USAGE_RATELIMITER_SERVICE = "userUploadUsageRateLimiterService"
        const val USER_DOWNLOAD_USAGE_RATELIMITER_SERVICE = "userDownloadUsageRateLimiterService"
        const val DOWNLOAD_USAGE_RATELIMITER_SERVICE = "downloadUsageRateLimiterService"
        const val UPLOAD_USAGE_RATELIMITER_SERVICE = "uploadUsageRateLimiterService"
        const val URL_RATELIMITER_SERVICE = "urlRateLimiterService"
        const val URL_REPO_RATELIMITER_SERVICE = "urlRepoRateLimiterService"
        const val USER_URL_REPO_RATELIMITER_SERVICE = "userUrlRepoRateLimiterService"

    }

}
