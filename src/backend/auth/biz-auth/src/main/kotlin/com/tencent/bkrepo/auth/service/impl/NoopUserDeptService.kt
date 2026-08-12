package com.tencent.bkrepo.auth.service.impl

import com.tencent.bkrepo.auth.pojo.user.UserOrgMembership
import com.tencent.bkrepo.auth.service.UserDeptService
import org.slf4j.LoggerFactory

/**
 * 未注册任何 [UserDeptService] 实现时的空操作占位：返回空组织归属，仅打日志。
 *
 * 由 [AuthServiceConfig] 在缺少其它实现时注册，不要再加 `@Service`：
 * `@ConditionalOnMissingBean` 写在组件扫描类上会匹配到自身，导致 Bean 注册失败。
 */
class NoopUserDeptService : UserDeptService {

    override fun getUserOrgMembership(userId: String): UserOrgMembership {
        val normalized = userId.trim()
        logger.warn(
            "No UserDeptService implementation is available, return empty org scopes for user [$normalized]",
        )
        return UserOrgMembership(userId = normalized, scopes = emptyList())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NoopUserDeptService::class.java)
    }
}
