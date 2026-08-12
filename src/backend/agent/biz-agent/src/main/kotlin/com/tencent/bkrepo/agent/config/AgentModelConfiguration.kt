/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.config.properties.AgentModelAuthType
import com.tencent.bkrepo.agent.config.properties.AgentModelProperties
import io.agentscope.core.model.GenerateOptions
import io.agentscope.core.model.Model
import io.agentscope.extensions.model.openai.OpenAIChatModel
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AgentModelConfiguration {

    @Bean
    fun agentChatModel(properties: AgentModelProperties): Model {
        require(properties.baseUrl.isNotBlank()) { "agent.model.base-url is required" }
        require(properties.modelName.isNotBlank()) { "agent.model.model-name is required" }

        logger.info(
            "Initializing agent chat model: baseUrl={}, modelName={}, authType={}",
            properties.baseUrl,
            properties.modelName,
            properties.authType,
        )

        val builder = OpenAIChatModel.builder()
            .baseUrl(properties.baseUrl)
            .modelName(properties.modelName)
            .stream(properties.stream)
            .contextWindowSize(properties.contextWindowSize)

        when (properties.authType) {
            AgentModelAuthType.BK_GATEWAY -> {
                require(properties.bkAppCode.isNotBlank()) {
                    "agent.model.bk-app-code is required when auth-type=bk-gateway"
                }
                require(properties.bkAppSecret.isNotBlank()) {
                    "agent.model.bk-app-secret is required when auth-type=bk-gateway"
                }
                val authJson =
                    """{"bk_app_code":"${properties.bkAppCode}","bk_app_secret":"${properties.bkAppSecret}"}"""
                builder
                    .generateOptions(
                        GenerateOptions.builder()
                            .additionalHeader("X-Bkapi-Authorization", authJson)
                            .build(),
                    )
                    .endpointPath("")
            }
            AgentModelAuthType.API_KEY -> {
                require(properties.apiKey.isNotBlank()) {
                    "agent.model.api-key is required when auth-type=api-key"
                }
                builder.apiKey(properties.apiKey)
            }
        }
        return builder.build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentModelConfiguration::class.java)
    }
}
