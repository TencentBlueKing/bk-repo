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

package com.tencent.bkrepo.agent.tool

import io.agentscope.core.message.ToolResultBlock
import io.agentscope.core.permission.PermissionContextState
import io.agentscope.core.permission.PermissionDecision
import io.agentscope.core.tool.ToolBase
import io.agentscope.core.tool.ToolCallParam
import reactor.core.publisher.Mono

/**
 * HITL 冒烟工具：始终 ASK，用户确认后在后台直接返回结果，不依赖客户端本地执行。
 *
 * 用于验证 `REQUIRE_USER_CONFIRM` ↔ `confirmResults` 往返，联调通过后可关闭。
 */
class HitlSmokeTestTool : ToolBase(
    ToolBase.builder()
        .name(NAME)
        .description(
            "测试高风险操作确认流程。当用户要求测试确认、HITL 或批准流程时调用；" +
                "会先向用户请求确认，批准后再返回测试结果。",
        )
        .inputSchema(
            mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "message" to mapOf(
                        "type" to "string",
                        "description" to "确认通过后回显的测试消息",
                    ),
                ),
            ),
        )
        .readOnly(true)
        .concurrencySafe(true),
) {

    override fun checkPermissions(
        toolInput: MutableMap<String, Any>,
        context: PermissionContextState,
    ): Mono<PermissionDecision> = Mono.just(
        PermissionDecision.ask("测试确认：是否批准执行 hitl_smoke_test？"),
    )

    override fun callAsync(param: ToolCallParam): Mono<ToolResultBlock> {
        val input = param.toolUseBlock.input
        val message = input["message"]?.toString()?.takeIf { it.isNotBlank() }
            ?: "HITL 冒烟测试通过"
        return Mono.just(
            ToolResultBlock.text("已确认：$message")
                .withIdAndName(param.toolUseBlock.id, param.toolUseBlock.name),
        )
    }

    companion object {
        const val NAME = "hitl_smoke_test"
    }
}
