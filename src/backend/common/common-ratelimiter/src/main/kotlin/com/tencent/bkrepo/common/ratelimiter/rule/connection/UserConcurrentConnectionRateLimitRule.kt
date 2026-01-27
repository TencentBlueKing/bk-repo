package com.tencent.bkrepo.common.ratelimiter.rule.connection

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户并发连接数限流规则
 */
class UserConcurrentConnectionRateLimitRule(
    private val userLimitRules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()
) : RateLimitRule {

    override fun isEmpty(): Boolean = userLimitRules.isEmpty()

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        val resourceLimit = userLimitRules[resInfo.resource] ?: return null
        return ResLimitInfo(resInfo.resource, resourceLimit)
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        userLimitRules[resourceLimit.resource] = resourceLimit
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach { addRateLimitRule(it) }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.USER_CONCURRENT_CONNECTION.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }
}
