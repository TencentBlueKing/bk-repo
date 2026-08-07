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

package com.tencent.bkrepo.agent.tool.local

import io.agentscope.core.message.ToolResultBlock
import io.agentscope.core.permission.PermissionContextState
import io.agentscope.core.permission.PermissionDecision
import io.agentscope.core.tool.ToolBase
import io.agentscope.core.tool.ToolCallParam
import io.agentscope.core.tool.ToolSuspendException
import reactor.core.publisher.Mono

/**
 * 客户端本地执行的 SchemaOnly 工具。
 *
 * - 只读工具：直接 ALLOW，挂起后由客户端 [REQUIRE_EXTERNAL_EXECUTION] 执行。
 * - 写操作工具：Permission ASK，用户 confirmResults 批准后再挂起、交客户端执行。
 */
class ExternalLocalTool(
    definition: LocalToolDefinition,
    private val requiresConfirmation: Boolean,
) : ToolBase(
    ToolBase.builder()
        .name(definition.name)
        .description(definition.description)
        .inputSchema(definition.inputSchema)
        .externalTool(true)
        .readOnly(!requiresConfirmation)
        .concurrencySafe(true),
) {

    override fun checkPermissions(
        toolInput: MutableMap<String, Any>,
        context: PermissionContextState,
    ): Mono<PermissionDecision> = Mono.just(
        if (requiresConfirmation) {
            PermissionDecision.ask(confirmMessage(name, toolInput))
        } else {
            PermissionDecision.allow("Read-only local tool '$name'")
        },
    )

    override fun callAsync(param: ToolCallParam): Mono<ToolResultBlock> =
        Mono.error(ToolSuspendException())

    companion object {
        private fun confirmMessage(name: String, input: Map<String, Any>): String = when (name) {
            "pause_download_tasks" -> "暂停 ${taskCount(input)} 个下载任务"
            "resume_download_tasks" -> "恢复 ${taskCount(input)} 个下载任务"
            "requeue_download_tasks" -> "按当前下载目录重新入队 ${taskCount(input)} 个失败任务"
            "delete_download_tasks" ->
                "删除 ${taskCount(input)} 个下载任务（未完成会删已下载部分，已完成只删记录）"
            "update_download_settings" -> "修改下载配置"
            "run_cleanup" -> "按当前清理策略删除过期的已完成下载文件"
            "restart_download_engine" -> "重启下载引擎（进行中的传输会中断）"
            "clear_completed_tasks" -> "清空全部已完成下载记录（磁盘文件保留）"
            else -> "执行 $name"
        }

        private fun taskCount(input: Map<String, Any>): Int {
            val raw = input["taskIds"]
            return if (raw is Collection<*>) raw.size else 0
        }
    }
}
