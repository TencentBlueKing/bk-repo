package com.tencent.bkrepo.common.ratelimiter.rule.concurrent

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.PathResourceLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils.getUserAndPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户+URL并发请求限流规则
 *
 * resource 格式：userId:urlPath（如 alice:/generic/blobs/ 或 *:/generic/）
 *   - * 作为通配符匹配任意用户（StringPool.POUND = "*"）
 *   - urlPath 为空（如 alice:）时对该用户所有 URL 生效
 *
 * 匹配优先级：精确用户+URL前缀 → 通配用户+URL前缀 → 精确用户兜底 → 通配用户兜底
 */
class UserUrlConcurrentRequestRateLimitRule : RateLimitRule {

    // userId -> 该用户的 URL 前缀规则树
    private val userUrlLimitRules = ConcurrentHashMap<String, PathResourceLimitRule>()

    // userId -> 用户级兜底规则（不区分 URL）
    private val userLimitRules = ConcurrentHashMap<String, ResourceLimit>()

    override fun isEmpty(): Boolean = userUrlLimitRules.isEmpty() && userLimitRules.isEmpty()

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        val resource = resInfo.resource  // "alice:/generic/blobs/..."
        if (resource.isBlank()) return null
        val (userId, urlPath) = try {
            getUserAndPath(resource)
        } catch (e: Exception) {
            return null
        }

        // 1. 精确用户 + URL 前缀匹配
        userUrlLimitRules[userId]?.getPathResourceLimit(urlPath)
            ?.let { return ResLimitInfo(resource, it.copy(resource = "$userId:${it.resource}")) }

        // 2. 通配用户(#) + URL 前缀匹配
        userUrlLimitRules[StringPool.POUND]?.getPathResourceLimit(urlPath)
            ?.let { return ResLimitInfo(resource, it.copy(resource = "${StringPool.POUND}:${it.resource}")) }

        // 3. 精确用户兜底（不区分 URL）
        userLimitRules[userId]?.let { return ResLimitInfo(resource, it) }

        // 4. 通配用户兜底
        userLimitRules[StringPool.POUND]?.let { return ResLimitInfo(resource, it) }

        return null
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        filterResourceLimit(resourceLimit)
        val (userId, urlPath) = getUserAndPath(resourceLimit.resource)
        if (urlPath.isEmpty()) {
            userLimitRules[userId] = resourceLimit
        } else {
            if (!urlPath.startsWith("/")) {
                logger.warn("invalid urlPath in resource '${resourceLimit.resource}', expected leading '/'")
                throw InvalidResourceException(urlPath)
            }
            val rule = userUrlLimitRules.getOrPut(userId) { PathResourceLimitRule() }
            rule.addRateLimitRule(resourceLimit.copy(resource = urlPath))
        }
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach {
            try {
                addRateLimitRule(it)
            } catch (e: Exception) {
                logger.error("add config $it for ${this.javaClass.simpleName} failed: ${e.message}")
            }
        }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        if (resourceLimit.limitDimension != LimitDimension.USER_URL_CONCURRENT_REQUEST.name) {
            throw InvalidResourceException(resourceLimit.limitDimension)
        }
        if (resourceLimit.resource.isBlank()) {
            throw InvalidResourceException(resourceLimit.resource)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(UserUrlConcurrentRequestRateLimitRule::class.java)
    }
}
