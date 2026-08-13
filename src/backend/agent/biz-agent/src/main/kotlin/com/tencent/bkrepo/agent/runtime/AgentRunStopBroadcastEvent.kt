/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

/** 跨组件 stop 广播：本 JVM 内幂等触发本地 handle 中断。 */
data class AgentRunStopBroadcastEvent(
    val scope: ActiveRunScope,
    val runId: String,
)
