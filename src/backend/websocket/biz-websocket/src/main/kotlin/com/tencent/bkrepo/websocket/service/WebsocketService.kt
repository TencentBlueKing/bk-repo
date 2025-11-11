/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.websocket.service

import com.tencent.bkrepo.websocket.pojo.LightweightSessionInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Service
class WebsocketService {
    // 统一的会话存储，包含sessionId和关闭回调
    private val activeSessions = ConcurrentHashMap<String, LightweightSessionInfo>()

    fun addActiveSession(sessionId: String, session: WebSocketSession) {
        // Check if session already exists
        if (activeSessions.containsKey(sessionId)) {
            logger.warn("Session[$sessionId] already exists in cache")
            return
        }
        
        // Create lightweight session info with close callback only
        val sessionInfo = LightweightSessionInfo(
            sessionId = sessionId,
            closeCallback = {
                try {
                    if (session.isOpen) {
                        session.close(CloseStatus.GOING_AWAY)
                    }
                } catch (e: Exception) {
                    logger.warn("Exception occurred while closing WebSocket connection: ${e.message}", e)
                }
            }
        )
        
        activeSessions[sessionId] = sessionInfo
        logger.debug("Added active session: $sessionId, current connection count: ${activeSessions.size}")
    }

    // Clear cached session from instance
    fun removeCacheSession(sessionId: String) {
        activeSessions.remove(sessionId)
        logger.debug("Removed session: $sessionId, current connection count: ${activeSessions.size}")
    }

    /**
     * Get current connection count
     */
    fun getConnectionCount(): Int = activeSessions.size

    /**
     * Close all WebSocket connections
     */
    fun closeAllConnections() {
        val currentCount = activeSessions.size
        logger.info("Starting to close all WebSocket connections, current active connections: $currentCount")
        
        if (currentCount == 0) {
            logger.info("No active connections to close")
            return
        }
        
        // Execute close callbacks in parallel
        activeSessions.values.parallelStream().forEach { sessionInfo ->
            try {
                sessionInfo.closeCallback()
                logger.debug("Closed WebSocket connection: ${sessionInfo.sessionId}")
            } catch (e: Exception) {
                logger.warn("Exception occurred while closing WebSocket connection ${sessionInfo.sessionId}: ", e)
            }
        }
        
        // Clear all caches
        activeSessions.clear()
        
        logger.info("All WebSocket connections have been closed")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WebsocketService::class.java)
    }
}
