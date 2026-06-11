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

/**
 * URL前缀下载限速规则，pathLengthCheck=false 实现前缀匹配：
 * 配置 /proj/repo/bigfiles/ 可命中 /proj/repo/bigfiles/a.zip 等所有子路径。
 */
class UrlPrefixDownloadBandwidthRateLimitRule : RateLimitRule {

    val downloadBandwidthLimitRules: PathResourceLimitRule = PathResourceLimitRule(pathLengthCheck = false)

    override fun isEmpty(): Boolean = downloadBandwidthLimitRules.isEmpty()

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        if (resInfo.resource.isBlank()) return null
        val ruleLimit = downloadBandwidthLimitRules.getPathResourceLimit(resInfo.resource) ?: return null
        return ResLimitInfo(resInfo.resource, ruleLimit)
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        downloadBandwidthLimitRules.addPathResourceLimit(resourceLimit, dimensionList)
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
        if (resourceLimit.limitDimension != LimitDimension.URL_PREFIX_DOWNLOAD_BANDWIDTH.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UrlPrefixDownloadBandwidthRateLimitRule::class.java)
        private val dimensionList = listOf(LimitDimension.URL_PREFIX_DOWNLOAD_BANDWIDTH.name)
    }
}
