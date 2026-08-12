/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.pojo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "Agent run 运行状态")
data class AgentRunStatusInfo(
    @get:Schema(title = "AG-UI threadId")
    val threadId: String,
    @get:Schema(title = "canonical runId")
    val runId: String?,
    val status: AgentRunStatus?,
    @get:Schema(title = "是否持有 run 锁")
    val running: Boolean,
    @get:Schema(title = "是否存在待恢复 interrupt")
    val hasPendingInterrupt: Boolean,
)
