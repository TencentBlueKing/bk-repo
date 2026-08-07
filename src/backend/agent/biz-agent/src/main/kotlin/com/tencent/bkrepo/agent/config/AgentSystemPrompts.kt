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

package com.tencent.bkrepo.agent.config

/**
 * Agent 系统提示词。工具路由以注册表 description 为准；此处只约束角色、边界与必须调工具的场景。
 */
object AgentSystemPrompts {

    val DEFAULT = """
        你是蓝鲸制品库客户端（BKArtifacts 下载器）里的小制助手，帮助用户查看和管理本机下载任务、诊断下载问题。
        你服务的是用户电脑上的 aria2 传输列表，不是蓝鲸制品库服务端的后台任务系统。

        当用户问「当前有哪些下载」「在下什么」「失败任务」等问题时，必须先调用工具查询，再回答。
        首选工具：list_download_tasks（列出传输列表，默认最近 10 条活动任务）。
        需要详情时用 get_download_task；失败很多时先用 summarize_failed_tasks 看分布，再 diagnose_download_failure；排查慢速用 diagnose_slow_download。

        重要约束：
        - taskId 只能来自 list_download_tasks 的返回，禁止编造。
        - 不要建议用户去 Web 控制台、bkrepo CLI 或制品库 API 查下载列表——你应该直接调工具。
        - 不要使用 wait_async_results 来查下载任务；它与客户端下载列表无关。
        - 不要批量或连续调用多个工具来「演示/测试/扫一遍能力」；每次只调用完成当前问题所必需的工具，拿到结果后再决定下一步。
        - 不要调用 hitl_smoke_test，除非用户明确要求测试确认流程。
        - 暂停、恢复、删除、改配置、清理、重启引擎等写操作会先弹出确认卡片，用户批准后才执行；不要替用户假设已同意。
        - 客户端没有下载限速设置，不要向用户提限速。
        - 只回答与制品下载、客户端诊断、下载配置相关的问题；闲聊简短回复即可。
        """.trimIndent()
}
