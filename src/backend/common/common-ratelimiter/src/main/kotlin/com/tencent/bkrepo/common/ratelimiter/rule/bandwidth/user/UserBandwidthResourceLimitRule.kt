package com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.user

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.rule.PathResourceLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.PathNode
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户带宽资源规则类
 * 支持用户级别、用户+项目级别、用户+仓库级别的带宽限流配置
 */
class UserBandwidthResourceLimitRule {

    // 用户+路径对应规则
    private val userBandwidthLimitRules: ConcurrentHashMap<String, PathResourceLimitRule> = ConcurrentHashMap()

    // 用户对应规则（不带路径）
    private val userLimitRules: ConcurrentHashMap<String, ResourceLimit> = ConcurrentHashMap()

    /**
     * 添加用户带宽资源限制
     */
    fun addUserBandwidthResourceLimit(resourceLimit: ResourceLimit) {
        val (userId, path) = ResourcePathUtils.getUserAndPath(resourceLimit.resource)
        if (path.isEmpty()) {
            userLimitRules[userId] = resourceLimit
        } else {
            val pathRule = userBandwidthLimitRules.getOrDefault(
                userId,
                PathResourceLimitRule(PathNode("/"), true)
            )
            pathRule.addPathResourceLimit(
                resourceLimit.copy(resource = path),
                userBandwidthDimensionList
            )
            userBandwidthLimitRules.putIfAbsent(userId, pathRule)
        }
    }

    /**
     * 获取路径对应的资源限制
     */
    fun getPathResourceLimit(userId: String, path: String): ResourceLimit? {
        val pathRule = userBandwidthLimitRules[userId] ?: userBandwidthLimitRules[StringPool.POUND]
        return pathRule?.getPathResourceLimit(path)
    }

    /**
     * 获取用户级别的资源限制
     */
    fun getUserResourceLimit(userId: String): ResourceLimit? {
        return userLimitRules[userId] ?: userLimitRules[StringPool.POUND]
    }

    companion object {
        private val userBandwidthDimensionList = listOf(
            LimitDimension.USER_UPLOAD_BANDWIDTH.name,
            LimitDimension.USER_DOWNLOAD_BANDWIDTH.name
        )
    }
}




