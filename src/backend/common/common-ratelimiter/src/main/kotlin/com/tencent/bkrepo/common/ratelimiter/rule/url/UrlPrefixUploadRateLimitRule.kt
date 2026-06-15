package com.tencent.bkrepo.common.ratelimiter.rule.url

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.PathResourceLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class UrlPrefixUploadRateLimitRule : RateLimitRule {

    val uploadUrlLimitRules: PathResourceLimitRule = PathResourceLimitRule()

    override fun isEmpty(): Boolean = uploadUrlLimitRules.isEmpty()

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        if (resInfo.resource.isBlank()) return null
        val ruleLimit = uploadUrlLimitRules.getPathResourceLimit(resInfo.resource) ?: return null
        return ResLimitInfo(resInfo.resource, ruleLimit)
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        uploadUrlLimitRules.addPathResourceLimit(resourceLimit, dimensionList)
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach {
            try {
                addRateLimitRule(it)
            } catch (e: Exception) {
                logger.error(
                    "add config $it for ${this.javaClass.simpleName} failed, error is ${e.message}"
                )
            }
        }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.URL_PREFIX_UPLOAD_RATE.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UrlPrefixUploadRateLimitRule::class.java)
        private val dimensionList = listOf(LimitDimension.URL_PREFIX_UPLOAD_RATE.name)
    }
}
