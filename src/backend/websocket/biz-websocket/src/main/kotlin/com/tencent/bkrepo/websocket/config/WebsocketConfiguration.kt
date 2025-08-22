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

package com.tencent.bkrepo.websocket.config

import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.websocket.constant.APP_ENDPOINT
import com.tencent.bkrepo.websocket.constant.DESKTOP_ENDPOINT
import com.tencent.bkrepo.websocket.constant.USER_ENDPOINT
import com.tencent.bkrepo.websocket.dispatch.push.TransferPush
import com.tencent.bkrepo.websocket.handler.SessionWebSocketHandlerDecoratorFactory
import com.tencent.bkrepo.websocket.listener.TransferPushListener
import com.tencent.bkrepo.websocket.service.WebsocketService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import java.util.function.Consumer

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties::class)
class WebsocketConfiguration(
    private val webSocketProperties: WebSocketProperties,
    private val websocketService: WebsocketService,
    private val jwtAuthProperties: JwtAuthProperties,
    private val authenticationManager: AuthenticationManager,
    private val webSocketMetrics: WebSocketMetrics,
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.setCacheLimit(webSocketProperties.cacheLimit)
        config.enableSimpleBroker("/topic")
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint(USER_ENDPOINT, APP_ENDPOINT, DESKTOP_ENDPOINT)
            .setAllowedOriginPatterns("*")
        registry.addEndpoint(USER_ENDPOINT, APP_ENDPOINT, DESKTOP_ENDPOINT)
            .setAllowedOriginPatterns("*")
            .withSockJS()
    }

    @Override
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        var defaultCorePoolSize = webSocketProperties.minThread
        if (defaultCorePoolSize < Runtime.getRuntime().availableProcessors() * 2) {
            defaultCorePoolSize = Runtime.getRuntime().availableProcessors() * 2
        }
        registration.taskExecutor().corePoolSize(defaultCorePoolSize)
            .maxPoolSize(defaultCorePoolSize * 2)
            .keepAliveSeconds(60)
    }

    @Override
    override fun configureClientOutboundChannel(registration: ChannelRegistration) {
        var defaultCorePoolSize = webSocketProperties.minThread
        if (defaultCorePoolSize < Runtime.getRuntime().availableProcessors() * 2) {
            defaultCorePoolSize = Runtime.getRuntime().availableProcessors() * 2
        }
        registration.taskExecutor().corePoolSize(defaultCorePoolSize).maxPoolSize(defaultCorePoolSize * 2)
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.addDecoratorFactory(wsHandlerDecoratorFactory())
        registration.setMessageSizeLimit(webSocketProperties.messageSizeLimit)
        registration.setSendTimeLimit(webSocketProperties.sendTimeLimit)
        registration.setSendBufferSizeLimit(webSocketProperties.sendBufferSizeLimit)
        super.configureWebSocketTransport(registration)
    }

    @Bean
    fun wsHandlerDecoratorFactory(): SessionWebSocketHandlerDecoratorFactory {
        return SessionWebSocketHandlerDecoratorFactory(
            websocketService = websocketService,
            authenticationManager = authenticationManager,
            jwtAuthProperties = jwtAuthProperties,
            webSocketMetrics = webSocketMetrics
        )
    }

    @Bean
    fun websocketTransferConsumer(transferPushListener: TransferPushListener): Consumer<Message<TransferPush>> {
        return Consumer { transferPushListener.accept(it) }
    }
}
