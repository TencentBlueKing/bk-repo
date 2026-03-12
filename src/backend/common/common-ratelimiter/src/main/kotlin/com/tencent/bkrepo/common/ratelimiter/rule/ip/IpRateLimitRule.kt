package com.tencent.bkrepo.common.ratelimiter.rule.ip

import com.tencent.bkrepo.common.api.util.IpUtils
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * IP限流规则
 * 根据配置的 resource 字段中的 IP 或 CIDR 网段进行限流
 * 配置示例：
 * - resource: "/ip/192.168.1.1" - 限流特定 IP
 * - resource: "/ip/10.0.0.0/8" - 限流特定网段
 * - resource: "/ip" - 限流所有 IP（向后兼容）
 */
class IpRateLimitRule : RateLimitRule {

    // 存储所有 IP 限流规则，key 为 resource 路径
    private val ipLimitRules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()
    
    // 存储通用规则（resource 为 "/ip"），用于限流所有 IP
    private var globalRule: ResourceLimit? = null

    override fun isEmpty(): Boolean {
        return ipLimitRules.isEmpty() && globalRule == null
    }

    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        // 从 extraResource 中获取客户端 IP（由 IpRateLimiterService.buildExtraResource 提供）
        val clientIp = resInfo.extraResource.firstOrNull() ?: return null
        
        // 遍历所有规则，查找匹配的规则
        var matchedRule: ResourceLimit? = null
        
        // 先检查精确 IP 匹配
        val exactIpResource = "/ip/$clientIp"
        matchedRule = ipLimitRules[exactIpResource]
        
        // 如果未找到精确匹配，检查 CIDR 网段匹配
        if (matchedRule == null) {
            matchedRule = ipLimitRules.entries.firstOrNull { (resource, _) ->
                val ipOrCidr = extractIpOrCidrFromResource(resource)
                ipOrCidr != null && ipOrCidr.contains('/') && try {
                    IpUtils.isInRange(clientIp, ipOrCidr)
                } catch (e: Exception) {
                    logger.warn(
                        "Invalid CIDR format [$ipOrCidr] in IP rate limit resource [$resource], e: ${e.message}"
                    )
                    false
                }
            }?.value
        }
        
        // 如果仍未找到，使用全局规则（resource 为 "/ip"）
        if (matchedRule == null) {
            matchedRule = globalRule
        }
        
        return matchedRule?.let { ResLimitInfo(resInfo.resource, it) }
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        val resource = resourceLimit.resource
        if (!resource.startsWith("/ip")) {
            logger.warn("Invalid IP rate limit resource path [$resource], should start with /ip")
            return
        }
        
        if (resource == "/ip") {
            // 全局规则，限流所有 IP
            globalRule = resourceLimit
        } else {
            // 特定 IP 或网段规则
            val ipOrCidr = extractIpOrCidrFromResource(resource)
            if (ipOrCidr == null) {
                logger.warn("Invalid IP rate limit resource path [$resource], cannot extract IP or CIDR")
                return
            }
            // 验证 IP 或 CIDR 格式
            if (ipOrCidr.contains('/')) {
                try {
                    IpUtils.parseCidr(ipOrCidr)
                } catch (e: Exception) {
                    logger.warn(
                        "Invalid CIDR format [$ipOrCidr] in IP rate limit resource [$resource], e: ${e.message}"
                    )
                    return
                }
            }
            ipLimitRules[resource] = resourceLimit
        }
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach { addRateLimitRule(it) }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) {
        // 不过滤，所有 IP 限流规则都接受
    }

    /**
     * 从 resource 路径中提取 IP 或 CIDR
     * 例如："/ip/192.168.1.1" -> "192.168.1.1"
     *      "/ip/10.0.0.0/8" -> "10.0.0.0/8"
     */
    private fun extractIpOrCidrFromResource(resource: String): String? {
        return if (resource.startsWith("/ip/")) {
            resource.substring(4)
        } else {
            null
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(IpRateLimitRule::class.java)
    }
}


