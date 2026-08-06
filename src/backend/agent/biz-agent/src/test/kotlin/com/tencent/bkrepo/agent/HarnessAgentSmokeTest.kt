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

package com.tencent.bkrepo.agent

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tencent.bkrepo.agent.config.AgentModelConfiguration
import com.tencent.bkrepo.agent.config.HarnessAgentConfiguration
import com.tencent.bkrepo.agent.config.properties.AgentModelProperties
import com.tencent.bkrepo.agent.config.properties.AgentProperties
import io.agentscope.core.agent.RuntimeContext
import io.agentscope.core.event.AgentEvent
import io.agentscope.core.event.TextBlockDeltaEvent
import io.agentscope.core.message.UserMessage
import io.agentscope.core.state.InMemoryAgentStateStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 用桩模型服务跑通一次完整的[io.agentscope.harness.agent.HarnessAgent]会话。
 *
 * 这里验证的是集成风险而不是业务逻辑：AgentScope声明依赖okhttp 5，但仓库统一把okhttp锁在4.x，
 * 编译期发现不了这种降级，只有真正发出一次模型请求才会暴露链接错误。
 */
@DisplayName("HarnessAgent与蓝鲸模型网关的OpenAI兼容协议冒烟测试")
class HarnessAgentSmokeTest {

    private lateinit var server: HttpServer
    private val receivedPaths = CopyOnWriteArrayList<String>()
    private val receivedBodies = CopyOnWriteArrayList<String>()

    @BeforeEach
    fun startStubModelServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> respondWithChatCompletionChunks(exchange) }
        server.start()
    }

    @AfterEach
    fun stopStubModelServer() {
        server.stop(0)
    }

    @Test
    fun `模型返回纯文本时ReAct循环应自然结束并流出文本增量`(@TempDir workspace: Path) {
        val agentProperties = AgentProperties().apply {
            this.workspace = workspace.toString()
            maxIters = 3
        }
        val modelProperties = AgentModelProperties(
            baseUrl = "http://127.0.0.1:${server.address.port}/v1",
            apiKey = "stub-api-key",
            modelName = "stub-model",
            stream = true,
        )
        val modelConfiguration = AgentModelConfiguration()
        val harnessConfiguration = HarnessAgentConfiguration()
        val agent = harnessConfiguration.harnessAgent(
            properties = agentProperties,
            model = modelConfiguration.agentChatModel(modelProperties),
            stateStore = InMemoryAgentStateStore(),
        )
        val runtimeContext = RuntimeContext.builder()
            .userId("smoke-user")
            .sessionId("smoke-session")
            .build()

        val events: List<AgentEvent> = agent
            .streamEvents(UserMessage("smoke-user", "你好"), runtimeContext)
            .collectList()
            .block(Duration.ofSeconds(60))
            .orEmpty()

        val streamedText = events.filterIsInstance<TextBlockDeltaEvent>().joinToString("") { it.delta }
        assertEquals(REPLY_TEXT, streamedText)
        assertTrue(receivedPaths.isNotEmpty()) { "桩模型服务未收到任何请求" }
        assertTrue(receivedBodies.any { it.contains("stub-model") }) {
            "请求体未携带配置的模型名: $receivedBodies"
        }
    }

    private fun respondWithChatCompletionChunks(exchange: HttpExchange) {
        exchange.use {
            receivedPaths.add(it.requestURI.path)
            receivedBodies.add(it.requestBody.readBytes().toString(StandardCharsets.UTF_8))
            it.responseHeaders.add("Content-Type", "text/event-stream")
            it.sendResponseHeaders(200, 0)
            val body = REPLY_TEXT.map { char -> deltaChunk("""{"content":"$char"}""") }
                .plus(deltaChunk("{}", finishReason = "stop"))
                .plus("data: [DONE]\n\n")
                .joinToString("")
            it.responseBody.write(body.toByteArray(StandardCharsets.UTF_8))
            it.responseBody.flush()
        }
    }

    private fun deltaChunk(delta: String, finishReason: String? = null): String {
        val finish = finishReason?.let { "\"$it\"" } ?: "null"
        return "data: {\"id\":\"chatcmpl-smoke\",\"object\":\"chat.completion.chunk\",\"created\":0," +
            "\"model\":\"stub-model\",\"choices\":[{\"index\":0,\"delta\":$delta," +
            "\"finish_reason\":$finish}]}\n\n"
    }

    companion object {
        private const val REPLY_TEXT = "你好"
    }
}
