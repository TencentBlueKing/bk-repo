package com.tencent.bkrepo.websocket.pojo

/**
 * Lightweight WebSocket session information
 * Only stores necessary close information to avoid memory overflow
 */
data class LightweightSessionInfo(
    val sessionId: String,
    val closeCallback: () -> Unit
)
