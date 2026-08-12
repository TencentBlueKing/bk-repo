/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.pojo

/** 对已终态 run 重放 AG-UI 事件，不重新执行 agent。 */
data class AgentRunReconnectRequest(
    val threadId: String,
    val runId: String,
)
