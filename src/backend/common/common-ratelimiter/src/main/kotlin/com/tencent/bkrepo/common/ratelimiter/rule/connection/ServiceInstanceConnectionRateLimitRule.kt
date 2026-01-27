package com.tencent.bkrepo.common.ratelimiter.rule.connection

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 服务实例连接数限流规则
 */
class ServiceInstanceConnectionRateLimitRule(
    private val instanceLimitRules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()
) : RateLimitRule {

    override fun isEmpty(): Boolean {
        return instanceLimitRules.isEmpty()
    }

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        val resourceLimit = instanceLimitRules[resInfo.resource] ?: return null
        return ResLimitInfo(resInfo.resource, resourceLimit)
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        instanceLimitRules[resourceLimit.resource] = resourceLimit
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach { addRateLimitRule(it) }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.SERVICE_INSTANCE_CONNECTION.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceInstanceConnectionRateLimitRule::class.java)
    }
}


