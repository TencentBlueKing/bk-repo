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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Agent 模型网关配置。通过 OpenAI 兼容协议接入蓝鲸模型网关
 */
@ConfigurationProperties("agent.model")
data class AgentModelProperties(
    /**
     * 模型网关 OpenAI 兼容接口地址，例如 https://bkapi.example.com/prod/openai/v1
     */
    var baseUrl: String = "",
    /**
     * 模型网关 API Key。生产环境建议通过配置中心或密钥服务注入
     */
    var apiKey: String = "",
    /**
     * 模型名称，例如 gpt-4o / qwen-max
     */
    var modelName: String = "",
    /**
     * 是否启用流式输出。Agent 对外以 SSE 推送事件，建议保持 true
     */
    var stream: Boolean = true,
    /**
     * 模型上下文窗口大小，用于 AgentScope 内部 token 预算
     */
    var contextWindowSize: Int = DEFAULT_CONTEXT_WINDOW_SIZE,
) {
    companion object {
        const val DEFAULT_CONTEXT_WINDOW_SIZE = 128_000
    }
}
