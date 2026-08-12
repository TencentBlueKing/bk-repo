/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

interface AgentResumeIdempotencyStore {

    /** @return true 表示首次见到该 resume 指纹，false 表示重复提交 */
    fun tryMark(threadId: String, interruptId: String, fingerprint: String): Boolean
}
