package com.tencent.bkrepo.websocket.service

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

/**
 * WebSocket关闭管理器
 * 负责在Spring应用关闭时优雅地断开所有WebSocket连接
 */
@Component
class WebSocketShutdownManager(
    private val websocketService: WebsocketService
) : SmartLifecycle {

    private var running = false
    private val logger = LoggerFactory.getLogger(WebSocketShutdownManager::class.java)

    override fun start() {
        running = true
        logger.info("WebSocket shutdown manager started")
    }

    override fun stop() {
        if (!running) {
            return
        }
        
        logger.info("Starting WebSocket connection shutdown process...")
        
        try {
            // Get connection count
            val connectionCount = websocketService.getConnectionCount()
            logger.info("Found $connectionCount active WebSocket connections")
            
            if (connectionCount == 0) {
                logger.info("No active WebSocket connections to close")
                return
            }

            // Use lightweight approach to close all connections
            websocketService.closeAllConnections()
            
            logger.info("All WebSocket connections have been successfully closed")
            
        } catch (e: Exception) {
            logger.error("Exception occurred while closing WebSocket connections: ${e.message}", e)
        } finally {
            running = false
        }
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = Integer.MAX_VALUE // Ensure shutdown after other components
}
