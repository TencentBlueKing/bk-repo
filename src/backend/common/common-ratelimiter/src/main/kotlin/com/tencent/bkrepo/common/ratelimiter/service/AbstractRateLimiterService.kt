/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.ratelimiter.service


import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.interceptor.MonitorRateLimiterInterceptorAdaptor
import com.tencent.bkrepo.common.ratelimiter.interceptor.RateLimiterInterceptor
import com.tencent.bkrepo.common.ratelimiter.interceptor.RateLimiterInterceptorChain
import com.tencent.bkrepo.common.ratelimiter.interceptor.TargetRateLimiterInterceptorAdaptor
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.DownloadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.UploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlRepoRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.url.user.UserUrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.url.user.UserUrlRepoRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.DownloadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.UploadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.user.UserDownloadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.user.UserUploadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.ratelimiter.utils.RateLimiterBuilder
import com.tencent.bkrepo.common.service.servlet.MultipleReadHttpRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.util.unit.DataSize
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.http.HttpServletRequest

/**
 * 限流器抽象实现
 */
abstract class AbstractRateLimiterService(
    private val taskScheduler: ThreadPoolTaskScheduler,
    val rateLimiterProperties: RateLimiterProperties,
    private val rateLimiterMetrics: RateLimiterMetrics,
    val redisTemplate: RedisTemplate<String, String>,
    private val rateLimiterConfigService: RateLimiterConfigService,
) : RateLimiterService {

    @Value("\${spring.application.name}")
    var moduleName: String = StringPool.EMPTY


    // 资源对应限限流算法缓存
    var rateLimiterCache: ConcurrentHashMap<String, RateLimiter> = ConcurrentHashMap(256)

    val interceptorChain: RateLimiterInterceptorChain =
        RateLimiterInterceptorChain(
            mutableListOf(
                MonitorRateLimiterInterceptorAdaptor(rateLimiterMetrics),
                TargetRateLimiterInterceptorAdaptor(rateLimiterConfigService)
            )
        )

    // 限流规则配置
    var rateLimitRule: RateLimitRule? = null

    // 当前限流规则配置hashcode
    var currentRuleHashCode: Int? = null

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshRateLimitRule, rateLimiterProperties.refreshDuration * 1000)
        initCompanion()
    }


    open fun initCompanion() = Unit

    /**
     * 获取对应资源限流规则配置
     */
    fun getResLimitInfoAndResInfo(request: HttpServletRequest): Pair<ResLimitInfo?, ResInfo?> {
        if (!rateLimiterProperties.enabled) {
            return Pair(null, null)
        }
        return try {
            // 构建资源信息
            val resInfo = ResInfo(
                resource = buildResource(request),
                extraResource = buildExtraResource(request)
            )
            Pair(rateLimitRule?.getRateLimitRule(resInfo), resInfo)
        } catch (e: InvalidResourceException) {
            // 记录无效资源配置警告日志
            logger.warn("Config is invalid for request ${request.requestURI}, e: ${e.message}")
            Pair(null, null)
        }
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        if (ignoreRequest(request)) return
        if (rateLimitRule == null || rateLimitRule!!.isEmpty()) return
        val resLimitInfo = getResLimitInfoAndResInfo(request).first ?: return
        rateLimitCatch(
            request = request,
            resLimitInfo = resLimitInfo,
            applyPermits = applyPermits,
        ) { rateLimiter, permits ->
            rateLimiter.tryAcquire(permits)
        }
    }

    override fun addInterceptor(interceptor: RateLimiterInterceptor) {
        this.interceptorChain.addInterceptor(interceptor)
    }

    override fun addInterceptors(interceptors: List<RateLimiterInterceptor>) {
        if (interceptors.isNotEmpty()) {
            this.interceptorChain.addInterceptors(interceptors)
        }
    }

    /**
     * 生成资源对应的唯一key
     */
    abstract fun generateKey(resource: String, resourceLimit: ResourceLimit): String

    /**
     * 根据请求获取对应的资源，用于查找对应限流规则
     */
    abstract fun buildResource(request: HttpServletRequest): String

    /**
     * 根据请求获取对其他资源信息，用于查找对应限流规则
     */
    abstract fun buildExtraResource(request: HttpServletRequest): List<String>

    /**
     * 根据请求获取需要申请的许可数
     */
    abstract fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long

    /**
     * 限流器实现对应的维度
     */
    abstract fun getLimitDimensions(): List<String>

    /**
     * 获取对应限流规则配置实现
     */
    abstract fun getRateLimitRuleClass(): Class<out RateLimitRule>

    /**
     * 对请求进行过滤，不进行限流
     */
    open fun ignoreRequest(request: HttpServletRequest): Boolean {
        return false
    }

    fun getRepoInfoFromAttribute(request: HttpServletRequest): Pair<String?, String?> {
        var projectId: String? = null
        var repoName: String? = null
        try {
            projectId = ((request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                as Map<*, *>)["projectId"] as String?
            repoName = ((request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                as Map<*, *>)["repoName"] as String?
        } catch (ignore: Exception) {
        }
        if (projectId.isNullOrEmpty()) {
            throw InvalidResourceException("Could not find projectId from request ${request.requestURI}")
        }
        return Pair(projectId, repoName)
    }

    fun getRepoInfoFromBody(request: HttpServletRequest): Pair<String?, String?> {
        val limit = DataSize.ofMegabytes(1).toBytes()
        val lengthCondition = request.contentLength in 1..limit
        val typeCondition = request.contentType?.startsWith(MediaType.APPLICATION_JSON_VALUE) == true
        // 限制缓存大小
        if (lengthCondition && typeCondition) {
            val multiReadRequest = MultipleReadHttpRequest(request, limit)
            val (projectId, repoName) = getRepoInfoFromQueryModel(multiReadRequest)
            if (!projectId.isNullOrEmpty()) return Pair(projectId, repoName)
            val (newProjectId, newRepoName) = getRepoInfoFromOtherRequest(multiReadRequest)
            if (newProjectId.isNullOrEmpty()) {
                throw InvalidResourceException("Could not find projectId from request ${request.requestURI}")
            }
            return Pair(newProjectId, newRepoName)
        }
        throw InvalidResourceException("Could not find projectId from body of request ${request.requestURI}")
    }

    private fun getRepoInfoFromQueryModel(multiReadRequest: MultipleReadHttpRequest): Pair<String?, String?> {
        try {
            val queryModel = multiReadRequest.inputStream.readJsonString<QueryModel>()
            val rule = queryModel.rule
            if (rule is Rule.NestedRule && rule.relation == Rule.NestedRule.RelationType.AND) {
                return findRepoInfoFromRule(rule)
            }
        } catch (ignore: Exception) {
        }
        return Pair(null, null)
    }

    private fun getRepoInfoFromOtherRequest(multiReadRequest: MultipleReadHttpRequest): Pair<String?, String?> {
        try {
            val mappedValue = objectMapper.readValue<Map<String, Any>>(multiReadRequest.inputStream)
            return Pair((mappedValue[PROJECT_ID] as? String), (mappedValue[REPO_NAME] as? String))
        } catch (ignore: Exception) {
            return Pair(null, null)
        }
    }

    private fun findRepoInfoFromRule(rule: Rule.NestedRule): Pair<String?, String?> {
        var projectId: String? = null
        var repoName: String? = null
        findKeyRule(PROJECT_ID, rule.rules)?.let {
            it.value.toString().apply { projectId = this }
        }
        findKeyRule(REPO_NAME, rule.rules)?.let {
            if (it.operation == OperationType.EQ) {
                it.value.toString().apply { repoName = this }
            }
        }
        return Pair(projectId, repoName)
    }

    private fun findKeyRule(key: String, rules: List<Rule>): Rule.QueryRule? {
        for (rule in rules) {
            if (rule is Rule.QueryRule && rule.field == key) {
                return rule
            }
        }
        return null
    }

    /**
     * 配置规则刷新
     */
    fun refreshRateLimitRule() {
        if (!rateLimiterProperties.enabled) return
        val usageRuleConfigs = rateLimiterProperties.rules.filter {
            it.limitDimension in getLimitDimensions()
        }
        val databaseConfig = try {
            rateLimiterConfigService.findByModuleNameAndLimitDimension(
                moduleName, getLimitDimensions().first()
            )
        } catch (e: Exception) {
            logger.error("system error: $e")
            listOf()
        }
        val configs = usageRuleConfigs.plus(databaseConfig.map { tRateLimit ->
            ResourceLimit(
                algo = tRateLimit.algo,
                resource = tRateLimit.resource,
                limit = tRateLimit.limit,
                limitDimension = tRateLimit.limitDimension,
                duration = tRateLimit.duration,
                capacity = tRateLimit.capacity,
                scope = tRateLimit.scope,
                targets = tRateLimit.targets
            )
        })
        // 配置规则变更后需要清理缓存的限流算法实现
        val newRuleHashCode = configs.hashCode()
        if (currentRuleHashCode == newRuleHashCode) {
            if (rateLimiterCache.size > rateLimiterProperties.cacheCapacity) {
                clearLimiterCache()
            }
            return
        }
        val usageRules = getRuleClass() ?: return
        usageRules.addRateLimitRules(configs)
        rateLimitRule = usageRules
        clearLimiterCache()
        currentRuleHashCode = newRuleHashCode
        initCompanionRateLimitRule()
        logger.info("rules in ${this.javaClass.simpleName} for request has been refreshed!")
    }

    open fun initCompanionRateLimitRule() = Unit


    private fun getRuleClass(): RateLimitRule? {
        return when (getRateLimitRuleClass()) {
            UrlRateLimitRule::class.java -> UrlRateLimitRule()
            UploadUsageRateLimitRule::class.java -> UploadUsageRateLimitRule()
            DownloadUsageRateLimitRule::class.java -> DownloadUsageRateLimitRule()
            UserDownloadUsageRateLimitRule::class.java -> UserDownloadUsageRateLimitRule()
            UserUploadUsageRateLimitRule::class.java -> UserUploadUsageRateLimitRule()
            UserUrlRateLimitRule::class.java -> UserUrlRateLimitRule()
            UploadBandwidthRateLimitRule::class.java -> UploadBandwidthRateLimitRule()
            DownloadBandwidthRateLimitRule::class.java -> DownloadBandwidthRateLimitRule()
            UrlRepoRateLimitRule::class.java -> UrlRepoRateLimitRule()
            UserUrlRepoRateLimitRule::class.java -> UserUrlRepoRateLimitRule()
            else -> null
        }
    }

    private fun clearLimiterCache() {
        rateLimiterCache.forEach {
            try {
                it.value.removeCacheLimit(it.key)
            } catch (e: Exception) {
                logger.warn("clear limiter cache error: ${e.cause}, ${e.message}")
            }
        }
        rateLimiterCache.clear()
    }

    private fun beforeRateLimitCheck(
        request: HttpServletRequest,
        applyPermits: Long? = null,
        resLimitInfo: ResLimitInfo,
        circuitBreakerPerSecond: Long? = null,
    ): Pair<RateLimiter, Long> {
        with(resLimitInfo) {
            val realPermits = getApplyPermits(request, applyPermits)
            interceptorChain.doBeforeLimitCheck(resource, resourceLimit)
            circuitBreakerCheck(resourceLimit, circuitBreakerPerSecond)
            val rateLimiter = getAlgorithmOfRateLimiter(resource, resourceLimit)
            return Pair(rateLimiter, realPermits)
        }
    }

    fun afterRateLimitCheck(
        resLimitInfo: ResLimitInfo,
        pass: Boolean,
        exception: Exception? = null,
    ) {
        with(resLimitInfo) {
            interceptorChain.doAfterLimitCheck(resource, resourceLimit, pass, exception)
        }
    }

    fun rateLimitCatch(
        request: HttpServletRequest,
        resLimitInfo: ResLimitInfo,
        applyPermits: Long? = null,
        circuitBreakerPerSecond: Long? = null,
        action: (RateLimiter, Long) -> Boolean
    ) {
        var exception: Exception? = null
        var pass = false
        try {
            val (rateLimiter, permits) = beforeRateLimitCheck(
                request = request,
                applyPermits = applyPermits,
                resLimitInfo = resLimitInfo,
                circuitBreakerPerSecond = circuitBreakerPerSecond
            )
            val realPermits = permits
            pass = action(rateLimiter, realPermits)
            if (!pass) {
                val msg = "${resLimitInfo.resource} has exceeded max rate limit: " +
                    "${resLimitInfo.resourceLimit.limit} /${resLimitInfo.resourceLimit.duration}"
                if (rateLimiterProperties.dryRun) {
                    logger.warn(msg)
                } else {
                    throw OverloadException(msg)
                }
            }
        } catch (e: OverloadException) {
            throw e
        } catch (e: AcquireLockFailedException) {
            logger.warn(
                "acquire lock failed for ${resLimitInfo.resource}" +
                    " with ${resLimitInfo.resourceLimit}, e: ${e.message}"
            )
            exception = e
        } catch (e: InvalidResourceException) {
            logger.warn("${resLimitInfo.resourceLimit} is invalid ${resLimitInfo.resource} , e: ${e.message}")
            exception = e
        } catch (e: Exception) {
            logger.error("internal error: $e")
            exception = e
        } finally {
            afterRateLimitCheck(resLimitInfo, pass, exception)
        }
    }

    /**
     * 获取对应限流算法实现
     */
    fun getAlgorithmOfRateLimiter(
        resource: String, resourceLimit: ResourceLimit
    ): RateLimiter {
        val limitKey = generateKey(resource, resourceLimit)
        return RateLimiterBuilder.getAlgorithmOfRateLimiter(
            limitKey, resourceLimit, redisTemplate, rateLimiterCache
        )
    }

    /**
     * （特殊操作）如果配置的限流规则比熔断配置小，则直接限流
     */
    fun circuitBreakerCheck(
        resourceLimit: ResourceLimit,
        circuitBreakerPerSecond: Long? = null,
    ) {
        if (circuitBreakerPerSecond == null) return
        val permitsPerSecond = resourceLimit.limit / resourceLimit.duration.seconds
        if (circuitBreakerPerSecond >= permitsPerSecond) {
            throw OverloadException(
                "The circuit breaker is activated when too many download requests are made to the service!"
            )
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AbstractRateLimiterService::class.java)
        val UPLOAD_REQUEST_METHOD = listOf(HttpMethod.POST.name, HttpMethod.PUT.name, HttpMethod.PATCH.name)
        val DOWNLOAD_REQUEST_METHOD = listOf(HttpMethod.GET.name)
    }
}
