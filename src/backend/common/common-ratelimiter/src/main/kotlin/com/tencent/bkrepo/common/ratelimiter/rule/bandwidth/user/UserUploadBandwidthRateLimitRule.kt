package com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.user

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户上传带宽限流配置规则实现
 */
open class UserUploadBandwidthRateLimitRule(
    private val userBandwidthLimitRules: ConcurrentHashMap<String, UserBandwidthResourceLimitRule> = ConcurrentHashMap()
) : RateLimitRule {

    override fun isEmpty(): Boolean {
        return userBandwidthLimitRules.isEmpty()
    }

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        var resLimitInfo = findConfigRule(resInfo.resource, resInfo.extraResource)
        if (resLimitInfo == null) {
            resLimitInfo = findConfigRule(resInfo.resource, resInfo.extraResource, true)
        }
        return resLimitInfo
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        val (userId, _) = ResourcePathUtils.getUserAndPath(resourceLimit.resource)
        val userBandwidthRule = userBandwidthLimitRules.getOrDefault(userId, UserBandwidthResourceLimitRule())
        userBandwidthRule.addUserBandwidthResourceLimit(resourceLimit)
        userBandwidthLimitRules.putIfAbsent(userId, userBandwidthRule)
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
        if (resourceLimit.limitDimension != LimitDimension.USER_UPLOAD_BANDWIDTH.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    private fun findConfigRule(
        resource: String,
        extraResource: List<String>,
        userPattern: Boolean = false
    ): ResLimitInfo? {
        var realResource = resource
        if (realResource.isBlank()) {
            return null
        }
        var (user, resWithoutUser) = ResourcePathUtils.getUserAndPath(realResource)
        if (userPattern) {
            user = StringPool.POUND
        }
        val userBandwidthRule = userBandwidthLimitRules[user]
        var ruleLimit = userBandwidthRule?.getPathResourceLimit(user, resWithoutUser)
            ?: userBandwidthRule?.getUserResourceLimit(user)

        if (ruleLimit == null && extraResource.isNotEmpty()) {
            for (res in extraResource) {
                var (_, resWithoutUser) = ResourcePathUtils.getUserAndPath(res)
                ruleLimit = userBandwidthRule?.getPathResourceLimit(user, resWithoutUser)
                    ?: userBandwidthRule?.getUserResourceLimit(user)
                if (ruleLimit != null) {
                    realResource = res
                    break
                }
            }
        }
        if (ruleLimit == null) return null
        val resourceLimitCopy = ruleLimit.copy(resource = ResourcePathUtils.buildUserPath(user, ruleLimit.resource))
        return ResLimitInfo(realResource, resourceLimitCopy)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserUploadBandwidthRateLimitRule::class.java)
    }
}




