package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.BadCredentialsException
import com.tencent.bkrepo.common.security.http.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.http.credentials.UsernamePasswordCredentials
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.util.JwtUtils
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.PASSWORD
import com.tencent.bkrepo.npm.exception.NpmLoginFailException
import com.tencent.bkrepo.npm.pojo.auth.NpmAuthResponse
import org.springframework.http.MediaType
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * npm登录handler
 */
class NpmLoginAuthHandler(
    private val authenticationManager: AuthenticationManager,
    private val jwtProperties: JwtAuthProperties
) : HttpAuthHandler {

    private val signingKey = JwtUtils.createSigningKey(jwtProperties.secretKey)

    override fun getLoginEndpoint() = NpmArtifactInfo.NPM_ADD_USER_MAPPING_URI

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        try {
            val jsonNode = JsonUtils.objectMapper.readTree(request.inputStream)
            val username = jsonNode[NAME].textValue()
            val password = jsonNode[PASSWORD].textValue()
            return UsernamePasswordCredentials(username, password)
        } catch (exception: IllegalArgumentException) {
            throw BadCredentialsException()
        }
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        with(authCredentials as UsernamePasswordCredentials) {
            return authenticationManager.checkUserAccount(username, password)
        }
    }

    override fun onAuthenticateSuccess(request: HttpServletRequest, response: HttpServletResponse, userId: String) {
        val token = JwtUtils.generateToken(signingKey, jwtProperties.expiration, userId)
        val authResponse = NpmAuthResponse.success("org.couchdb.user:$userId", token)
        response.status = HttpStatus.CREATED.value
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.print(authResponse.toJsonString())
        response.writer.flush()
    }

    override fun onAuthenticateFailed(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authenticationException: AuthenticationException
    ) {
        throw NpmLoginFailException(StringPool.EMPTY)
    }
}
