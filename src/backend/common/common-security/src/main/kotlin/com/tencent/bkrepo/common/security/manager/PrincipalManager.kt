package com.tencent.bkrepo.common.security.manager

import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory

class PrincipalManager(
    private val serviceUserClient: ServiceUserClient
) {

    fun checkPrincipal(userId: String, principalType: PrincipalType) {
        val platformId = SecurityUtils.getPlatformId()
        checkAnonymous(userId, platformId)

        if (principalType == PrincipalType.ADMIN) {
            if (!isAdminUser(userId)) {
                throw PermissionException()
            }
        } else if (principalType == PrincipalType.PLATFORM) {
            if (userId.isEmpty()) {
                logger.warn("platform auth with empty userId[$platformId,$userId]")
            }
            if (platformId == null && !isAdminUser(userId)) {
                throw PermissionException()
            }
        } else if (principalType == PrincipalType.GENERAL) {
            if (userId.isEmpty() || userId == ANONYMOUS_USER) {
                throw PermissionException()
            }
        }
    }

    /**
     * 检查是否为匿名用户，如果是匿名用户则返回401并提示登录
     */
    private fun checkAnonymous(userId: String, platformId: String?) {
        if (userId == ANONYMOUS_USER && platformId == null) {
            throw AuthenticationException()
        }
    }

    private fun isAdminUser(userId: String): Boolean {
        val tenantId = SecurityUtils.getTenantId()
        return serviceUserClient.userInfoByIdAndTenantId(userId, tenantId).data?.admin == true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PrincipalManager::class.java)
    }

}
