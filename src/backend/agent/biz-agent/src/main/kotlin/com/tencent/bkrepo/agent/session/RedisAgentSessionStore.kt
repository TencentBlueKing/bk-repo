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

package com.tencent.bkrepo.agent.session

import com.tencent.bkrepo.agent.constant.AGENT_SESSION_OWNER_KEY_PREFIX
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.common.security.exception.PermissionException

class RedisAgentSessionStore(
    private val redisOperation: RedisOperation,
    private val sessionTtlSeconds: Long,
) : AgentSessionStore {

    override fun bindSession(userId: String, projectId: String, threadId: String) {
        redisOperation.set(sessionKey(projectId, threadId), userId, sessionTtlSeconds)
    }

    override fun assertSessionOwner(userId: String, projectId: String, threadId: String) {
        val owner = redisOperation.get(sessionKey(projectId, threadId))
            ?: throw PermissionException("Session[$threadId] does not exist in project[$projectId]")
        if (owner != userId) {
            throw PermissionException("Session[$threadId] does not belong to user[$userId] in project[$projectId]")
        }
    }

    override fun touchSessionOwner(userId: String, projectId: String, threadId: String) {
        val key = sessionKey(projectId, threadId)
        if (redisOperation.get(key) == userId) {
            redisOperation.expire(key, sessionTtlSeconds)
        }
    }

    override fun removeSession(projectId: String, threadId: String) {
        redisOperation.delete(sessionKey(projectId, threadId))
    }

    private fun sessionKey(projectId: String, threadId: String): String {
        return "$AGENT_SESSION_OWNER_KEY_PREFIX$projectId:$threadId"
    }
}
