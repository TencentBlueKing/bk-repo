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

package com.tencent.bkrepo.agent.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AgentScope [io.agentscope.harness.agent.memory.compaction.CompactionConfig] 配置。
 *
 * 未显式设置的阈值沿用框架动态默认值（基于模型 context window）。
 */
@ConfigurationProperties("agent.compaction")
data class AgentCompactionProperties(
    /** 是否启用对话摘要压缩（CompactionMiddleware） */
    var enabled: Boolean = true,
    /** 消息数触发阈值；0 表示使用框架默认 */
    var triggerMessages: Int = DEFAULT_TRIGGER_MESSAGES,
    /** 压缩后保留的最近消息条数；0 表示使用框架默认 */
    var keepMessages: Int = DEFAULT_KEEP_MESSAGES,
    /** 压缩过程预留 token 缓冲（动态模式） */
    var reserved: Int = DEFAULT_RESERVED,
    /**
     * 压缩前是否 flush 长期记忆。第一期未启用 memory 工具，默认关闭。
     */
    var flushBeforeCompact: Boolean = false,
    /**
     * 压缩前是否 offload 到 session JSONL。第一期历史走 Mongo 归档，默认关闭。
     */
    var offloadBeforeCompact: Boolean = false,
) {
    companion object {
        const val DEFAULT_TRIGGER_MESSAGES = 0
        const val DEFAULT_KEEP_MESSAGES = 0
        const val DEFAULT_RESERVED = 20_000
    }
}

@ConfigurationProperties("agent.tool-result-eviction")
data class AgentToolResultEvictionProperties(
    /** 是否启用超大 tool 结果外置（ToolResultEvictionMiddleware） */
    var enabled: Boolean = true,
)
