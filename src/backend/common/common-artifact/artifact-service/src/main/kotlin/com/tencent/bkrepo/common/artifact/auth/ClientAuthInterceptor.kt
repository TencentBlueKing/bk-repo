package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.common.artifact.config.ANONYMOUS_USER
import com.tencent.bkrepo.common.artifact.config.PROJECT_ID
import com.tencent.bkrepo.common.artifact.config.REPO_NAME
import com.tencent.bkrepo.common.artifact.config.USER_KEY
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 依赖源客户端认证拦截器
 *
 * @author: carrypan
 * @date: 2019/11/22
 */
class ClientAuthInterceptor: HandlerInterceptorAdapter() {

    @Autowired
    private lateinit var clientAuthHandler: ClientAuthHandler

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val uri = request.requestURI
        val nameValueMap = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        val projectId = nameValueMap[PROJECT_ID]?.toString()
        val repoName = nameValueMap[REPO_NAME]?.toString()
        logger.debug("Prepare to authenticate, uri: [$uri]")
        return if(clientAuthHandler.needAuthenticate(uri, projectId, repoName)) {
            var userId: String? = null
            try {
                userId = clientAuthHandler.onAuthenticate(request)
                logger.debug("User[$userId] authenticate success.")
                clientAuthHandler.onAuthenticateSuccess(userId, request, response)
                true
            } catch (authException: ClientAuthException) {
                logger.warn("User[$userId] authenticate failed: $authException")
                clientAuthHandler.onAuthenticateFailed(request, response)
                false
            }
        } else {
            logger.debug("Skip authentication, set userId to anonymous.")
            request.setAttribute(USER_KEY, ANONYMOUS_USER)
            true
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClientAuthInterceptor::class.java)
    }
}