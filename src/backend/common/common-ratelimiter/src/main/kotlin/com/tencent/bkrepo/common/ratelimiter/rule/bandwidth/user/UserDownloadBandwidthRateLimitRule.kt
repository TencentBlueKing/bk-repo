package com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.user

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户下载带宽限流配置规则实现
 */
class UserDownloadBandwidthRateLimitRule(
    userBandwidthLimitRules: ConcurrentHashMap<String, UserBandwidthResourceLimitRule> = ConcurrentHashMap()
) : UserUploadBandwidthRateLimitRule(userBandwidthLimitRules) {

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.USER_DOWNLOAD_BANDWIDTH.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserDownloadBandwidthRateLimitRule::class.java)
    }
}




