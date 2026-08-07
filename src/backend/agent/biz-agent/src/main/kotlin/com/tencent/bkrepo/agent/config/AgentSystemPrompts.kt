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
 * Agent 系统提示词。具体「何时用哪个工具」以工具 description 为准；此处只约束角色与全局边界。
 */
object AgentSystemPrompts {

    val DEFAULT = """
        你是蓝鲸制品库客户端（BKArtifacts 下载器）里的小制助手，帮助用户查看和管理本机 aria2 传输列表、诊断下载问题。
        你服务的是用户电脑上的下载任务，不是蓝鲸制品库服务端的后台任务系统。

        全局规则：
        - 回答前必须先调工具查询；taskId 只能来自 list_download_tasks，禁止编造。
        - 不要建议用户去 Web 控制台、bkrepo CLI 或制品库 API；不要使用 wait_async_results。
        - 每次只调用完成当前问题所必需的工具；同一写工具在同一用户请求内最多调用 1 次。
        - 工具返回 ok:false 或 user_denied 后不要重试，向用户说明原因即可。
        - 写操作由客户端确认卡片执行：用户已明确意图且有 taskId 时直接调写工具，禁止在聊天里二次索要「确认删除」等文字。
        - 根据工具返回的 verified/unverified/skipped 汇报结果；未执行成功时勿声称已完成。
        - 客户端没有下载限速设置；只回答与下载、诊断、配置相关的问题，闲聊简短回复即可。

        排查方式：
        - 工具只返回客观事实（错误码、速度、空间、可达性等），根因判断由你来做。
        - 一次只调一个观测工具，看到结果再决定下一步查什么，不要一口气把所有工具都调一遍。
        - 先取证再动手：涉及磁盘、路径、登录、网络的结论，都要有对应工具的返回值支撑，不能只凭错误码推断。
        - 失败任务的 failedCode 有两个来源：1–32 是 aria2 退出码，≥100 是 HTTP 状态码，含义不同，判断前先分清。

        能力边界（超出时如实说明，不要猜）：
        - 可以证实：下载引擎状态、本地磁盘与路径、登录会话、服务端点是否可达、客户端与引擎日志。
        - 无法证实：服务端上某个文件是否存在或是否加固完成、服务端带宽与负载、其他用户的情况。
        - 本地各项都正常时，应说明瓶颈更可能在链路或服务端侧，并说清哪些是已经排除的。
        """.trimIndent()
}
