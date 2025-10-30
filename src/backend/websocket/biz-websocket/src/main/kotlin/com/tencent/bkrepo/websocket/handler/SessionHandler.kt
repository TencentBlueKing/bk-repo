/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.websocket.handler

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.CharPool.COLON
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.PLATFORM_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.util.TraceUtils
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.util.JwtUtils
import com.tencent.bkrepo.websocket.config.WebSocketMetrics
import com.tencent.bkrepo.websocket.constant.APP_ENDPOINT
import com.tencent.bkrepo.websocket.constant.DESKTOP_ENDPOINT
import com.tencent.bkrepo.websocket.constant.SESSION_ID
import com.tencent.bkrepo.websocket.constant.USER_ENDPOINT
import com.tencent.bkrepo.websocket.service.WebsocketService
import com.tencent.bkrepo.websocket.util.HostUtils
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.SignatureException
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.WebSocketHandlerDecorator
import java.util.Base64

class SessionHandler(
    delegate: WebSocketHandler,
    private val websocketService: WebsocketService,
    private val authenticationManager: AuthenticationManager,
    private val webSocketMetrics: WebSocketMetrics,
    jwtProperties: JwtAuthProperties,
    private val registry: ObservationRegistry
) : WebSocketHandlerDecorator(delegate) {

    private val signingKey = JwtUtils.createSigningKey(jwtProperties.secretKey)

    // 链接关闭记录去除session
    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        val uri = session.uri
        if (closeStatus.code != CloseStatus.NORMAL.code && closeStatus.code != CloseStatus.PROTOCOL_ERROR.code) {
            logger.warn("websocket close abnormal, [$closeStatus] [${session.uri}] [${session.remoteAddress}]")
        }
        val sessionId = HostUtils.getRealSession(session.uri?.query)
        if (sessionId.isNullOrEmpty()) {
            logger.warn("connection closed can not find sessionId, $uri| ${session.remoteAddress}")
        } else {
            websocketService.removeCacheSession(sessionId)
        }
        webSocketMetrics.connectionCount.decrementAndGet()
        super.afterConnectionClosed(session, closeStatus)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val uri = session.uri
        val remoteAddr = session.remoteAddress
        val sessionId = HostUtils.getRealSession(uri?.query)
        try {
            authorization(session)
        } catch (e: Exception) {
            val authException = e is AuthenticationException || e is ExpiredJwtException ||
                e is UnsupportedJwtException || e is MalformedJwtException ||
                e is SignatureException || e is IllegalArgumentException
            if (authException) {
                logger.info("auth failed: |$sessionId| $uri | $remoteAddr | ${e.message}")
                session.closeQuietly()
            } else {
                throw e
            }
        }
    }

    @Throws(java.lang.Exception::class)
    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        TraceUtils.newSpan(registry, "websocket.session", init = true) {
            super.handleMessage(session, message)
        }
    }

    private fun authorization(session: WebSocketSession) {
        val uri = session.uri
        val remoteId = session.remoteAddress
        val sessionId = HostUtils.getRealSession(uri?.query)
        when {
            uri == null -> throw AuthenticationException("uri is null")
            uri.path.startsWith(USER_ENDPOINT) || uri.path.startsWith(DESKTOP_ENDPOINT) -> {
                val platformToken = session.handshakeHeaders[HttpHeaders.AUTHORIZATION]?.firstOrNull()
                    ?.removePrefix(PLATFORM_AUTH_PREFIX) ?: throw AuthenticationException("platform credential is null")
                val (accessKey, secretKey) = String(Base64.getDecoder().decode(platformToken)).split(COLON)
                val appId = authenticationManager.checkPlatformAccount(accessKey, secretKey)
                session.attributes[PLATFORM_KEY] = appId
                session.attributes[USER_KEY] = session.handshakeHeaders[AUTH_HEADER_UID]?.first()
            }
            uri.path.startsWith(APP_ENDPOINT) -> {
                val token = session.handshakeHeaders[HttpHeaders.AUTHORIZATION]?.firstOrNull().orEmpty()
                val claims = JwtUtils.validateToken(signingKey, token).payload
                session.attributes[USER_KEY] = claims.subject
            }
            else -> throw AuthenticationException("invalid uri")
        }
        websocketService.addActiveSession(sessionId!!, session)
        session.attributes[SESSION_ID] = sessionId
        logger.info("connection success: |$sessionId| $uri | $remoteId | ${session.attributes[USER_KEY]} ")
        webSocketMetrics.connectionCount.incrementAndGet()
        super.afterConnectionEstablished(session)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SessionHandler::class.java)
    }
}
