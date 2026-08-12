/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

/** thread 级活跃 canonical runId，供 status/stop 与多副本查询。 */
interface AgentActiveRunStore {

    fun bind(userId: String, threadId: String, runId: String)

    fun get(userId: String, threadId: String): String?

    fun clear(userId: String, threadId: String)
}
