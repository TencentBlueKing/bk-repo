/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

/** run 级取消信号，供 stop API 跨副本通知进行中的 run。 */
interface AgentRunCancelStore {

    fun requestCancel(runId: String)

    fun isCancelled(runId: String): Boolean

    fun clear(runId: String)
}
