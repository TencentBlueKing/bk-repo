package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.auth.api.ServiceAccountResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.common.api.constant.APP_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.config.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.artifact.config.BASIC_AUTH_HEADER
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter

/**
 * 平台账户接入认证拦截器
 *
 * @author: carrypan
 * @date: 2019/12/23
 */
@Order(0)
class PlatformAuthInterceptor : HandlerInterceptorAdapter() {

    @Autowired
    private lateinit var serviceAccountResource: ServiceAccountResource

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        extractAuthCredentials(request)?.run {
            serviceAccountResource.checkCredential(first, second).data?.run {
                request.setAttribute(APP_KEY, this)
                val userId = request.getHeader(AUTH_HEADER_USER_ID)
                if (userId.isNullOrBlank()) {
                    request.setAttribute(USER_KEY, this)
                } else {
                    checkUserId(userId)
                    request.setAttribute(USER_KEY, userId)
                }
                logger.debug("Authenticate appId[$this] success.")
            } ?: throw ClientAuthException("AccessKey/AccessSecret check failed")
        }
        return true
    }

    private fun checkUserId(userId: String) {
        if (serviceUserResource.detail(userId).data == null) {
            val request = CreateUserRequest(userId = userId, name = userId, pwd = null)
            serviceUserResource.createUser(request)
            logger.info("Create user [$request] success.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PlatformAuthInterceptor::class.java)
        private const val THIRD_APP_AUTH_HEADER_PREFIX = "Platform "

        private fun extractAuthCredentials(request: HttpServletRequest): Pair<String, String>? {
            val basicAuthHeader = request.getHeader(BASIC_AUTH_HEADER)
            if (basicAuthHeader.isNullOrBlank()) return null
            return try {
                if (basicAuthHeader.startsWith(THIRD_APP_AUTH_HEADER_PREFIX)) {
                    val encodedCredentials = basicAuthHeader.removePrefix(THIRD_APP_AUTH_HEADER_PREFIX)
                    val decodedHeader = String(Base64.getDecoder().decode(encodedCredentials))
                    val parts = decodedHeader.split(":")
                    require(parts.size >= 2)
                    Pair(parts[0], parts[1])
                } else null
            } catch (exception: Exception) {
                throw ClientAuthException("Authorization value [$basicAuthHeader] is not a valid scheme")
            }
        }
    }
}
