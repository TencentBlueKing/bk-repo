/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.pojo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "Agent run 重连请求")
data class AgentRunReconnectRequest(
    @get:Schema(title = "AG-UI threadId（等同 sessionId）", required = true)
    val threadId: String,
    @get:Schema(title = "canonical runId", required = true)
    val runId: String,
)
