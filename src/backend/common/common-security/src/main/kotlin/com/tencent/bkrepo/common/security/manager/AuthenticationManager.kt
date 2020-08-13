package com.tencent.bkrepo.common.security.manager

import com.tencent.bkrepo.auth.api.ServiceAccountResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.HttpAuthProperties
import org.slf4j.LoggerFactory

/**
 * 认证管理器
 */
class AuthenticationManager(
    private val serviceUserResource: ServiceUserResource,
    private val serviceAccountResource: ServiceAccountResource,
    private val httpAuthProperties: HttpAuthProperties
) {

    fun checkUserAccount(uid: String, token: String): String {
        if (preCheck()) return uid
        val response = serviceUserResource.checkUserToken(uid, token)
        return if (response.data == true) uid else throw AuthenticationException("Authorization value check failed")
    }

    fun checkPlatformAccount(accessKey: String, secretKey: String): String {
        val response = serviceAccountResource.checkCredential(accessKey, secretKey)
        return response.data ?: throw AuthenticationException("AccessKey/SecretKey check failed.")
    }

    private fun preCheck(): Boolean {
        if (!httpAuthProperties.enabled) {
            logger.debug("Auth disabled, skip authenticate.")
            return true
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthenticationManager::class.java)
    }
}
