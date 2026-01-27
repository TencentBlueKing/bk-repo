package com.tencent.bkrepo.common.ratelimiter.rule.evict

import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import java.util.concurrent.ConcurrentHashMap

/**
 * 上传驱逐规则，支持按 user/ip/project/repo 四个维度匹配
 *
 * resource 格式：
 *   /user/{userId}           — 用户维度（优先级最高）
 *   /ip/{clientIp}           — IP 维度
 *   /{projectId}/{repoName}  — 项目/仓库维度
 *   /{projectId}             — 项目维度（优先级最低）
 *
 * ResourceLimit 字段含义：
 *   limit    — 最大存活秒数，超过则强制驱逐
 *   capacity — 最小保障秒数（可选），在此时间内不驱逐，默认 0
 */
class UploadEvictRateLimitRule : RateLimitRule {

    private val rules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()

    override fun isEmpty() = rules.isEmpty()

    /**
     * 按优先级查找匹配规则：user > ip > project/repo > project
     */
    override fun getRateLimitRule(resInfo: ResInfo): ResLimitInfo? {
        val candidates = listOf(resInfo.resource) + resInfo.extraResource
        for (candidate in candidates) {
            val rule = rules[candidate] ?: continue
            return ResLimitInfo(candidate, rule)
        }
        return null
    }

    override fun addRateLimitRule(resourceLimit: ResourceLimit) {
        rules[resourceLimit.resource] = resourceLimit
    }

    override fun addRateLimitRules(resourceLimit: List<ResourceLimit>) {
        resourceLimit.forEach { addRateLimitRule(it) }
    }

    override fun filterResourceLimit(resourceLimit: ResourceLimit) = Unit
}
