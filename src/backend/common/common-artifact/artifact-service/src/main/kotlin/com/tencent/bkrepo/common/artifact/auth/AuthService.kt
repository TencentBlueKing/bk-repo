package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.auth.api.ServiceAccountResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.artifact.config.AuthProperties
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 认证服务类
 *
 * @author: carrypan
 * @date: 2020/2/13
 */
@Component
class AuthService @Autowired constructor(
    private val serviceUserResource: ServiceUserResource,
    private val serviceAccountResource: ServiceAccountResource,
    private val authProperties: AuthProperties
) {

    fun checkUserAccount(uid: String, token: String): String {
        if (preCheck()) return uid
        val response = serviceUserResource.checkUserToken(uid, token)
        return if (response.data == true) uid else throw ClientAuthException("Authorization value check failed")
    }

    fun checkPlatformAccount(accessKey: String, secretKey: String): String {
        if (preCheck()) return DEFAULT_PLATFORM
        val response = serviceAccountResource.checkCredential(accessKey, secretKey)
        return response.data ?: throw ClientAuthException("AccessKey/SecretKey check failed.")
    }

    private fun preCheck(): Boolean {
        if (!authProperties.enabled) {
            logger.debug("Auth disabled, skip authenticate.")
            return true
        }
        return false
    }

    companion object {
        private const val DEFAULT_PLATFORM = "PLATFORM"
        private val logger = LoggerFactory.getLogger(AuthService::class.java)
    }
}
