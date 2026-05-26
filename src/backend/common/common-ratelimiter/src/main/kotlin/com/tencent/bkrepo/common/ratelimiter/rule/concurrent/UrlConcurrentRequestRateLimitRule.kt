package com.tencent.bkrepo.common.ratelimiter.rule.concurrent

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.PathResourceLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * URL并发请求限流规则
 */
class UrlConcurrentRequestRateLimitRule(
    private val pathResourceLimitRule: PathResourceLimitRule = PathResourceLimitRule()
) : RateLimitRule {

    override fun isEmpty(): Boolean = pathResourceLimitRule.isEmpty()

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        val resource = resInfo.resource
        if (resource.isBlank()) return null
        
        val ruleLimit = pathResourceLimitRule.getPathResourceLimit(resource) ?: return null
        return ResLimitInfo(resource, ruleLimit)
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        pathResourceLimitRule.addRateLimitRule(resourceLimit)
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach {
            try {
                addRateLimitRule(it)
            } catch (e: Exception) {
                logger.error("add config $it for ${this.javaClass.simpleName} failed, error is ${e.message}")
            }
        }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.URL_CONCURRENT_REQUEST.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UrlConcurrentRequestRateLimitRule::class.java)
    }
}
