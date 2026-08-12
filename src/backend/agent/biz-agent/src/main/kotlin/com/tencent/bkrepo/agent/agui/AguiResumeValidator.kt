/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agui

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.agent.session.AgentPendingInterruptStore
import com.tencent.bkrepo.agent.session.AgentResumeIdempotencyStore
import com.tencent.bkrepo.agent.session.PendingInterruptSnapshot
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import io.agentscope.core.agui.model.AguiResume
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.stereotype.Component

/**
 * 校验 AG-UI resume[]：覆盖全部 pending interrupt、拒绝非法 interruptId，并在写工具执行前重新 IAM 鉴权。
 */
@Component
class AguiResumeValidator(
    private val pendingInterruptStore: AgentPendingInterruptStore,
    private val resumeIdempotencyStore: AgentResumeIdempotencyStore,
    private val frontendToolCatalog: FrontendToolCatalog,
    private val permissionManager: PermissionManager,
    private val objectMapper: ObjectMapper,
) {

    fun validateAndPrepare(userId: String, projectId: String, input: RunAgentInput) {
        val threadId = input.threadId
        val pending = pendingInterruptStore.get(threadId)

        if (pending != null && !input.hasResume()) {
            throw ParameterInvalidException("resume", "thread[$threadId] has pending interrupts")
        }
        if (!input.hasResume()) {
            return
        }

        if (pending == null) {
            throw ParameterInvalidException("resume", "no pending interrupts for thread[$threadId]")
        }

        val resumeEntries = input.resume
        val resumeIds = resumeEntries.map { it.interruptId }.toSet()
        val pendingIds = pending.interrupts.map { it.id }.toSet()
        if (resumeIds != pendingIds) {
            throw ParameterInvalidException("resume", "resume must cover all pending interrupts exactly once")
        }

        for (entry in resumeEntries) {
            val snapshot = pending.interrupts.first { it.id == entry.interruptId }
            validateEntry(userId, projectId, threadId, snapshot, entry)
        }
    }

    private fun validateEntry(
        userId: String,
        projectId: String,
        threadId: String,
        snapshot: PendingInterruptSnapshot,
        entry: AguiResume,
    ) {
        if (!resumeIdempotencyStore.tryMark(threadId, entry.interruptId, fingerprint(entry))) {
            throw ParameterInvalidException("resume", "duplicate resume for interrupt[${entry.interruptId}]")
        }

        if (!isResolved(entry)) {
            return
        }

        val toolName = snapshot.toolName
        if (toolName.isNullOrBlank()) {
            return
        }

        if (frontendToolCatalog.find(toolName) == null) {
            throw ParameterInvalidException("resume", "tool[$toolName] is not allowed")
        }

        val approved = extractApproved(entry.payload)
        if (snapshot.requiresApproval && approved != true) {
            return
        }

        if (frontendToolCatalog.isWriteTool(toolName) && isExecutableResume(entry, snapshot)) {
            permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        }
    }

    private fun isExecutableResume(entry: AguiResume, snapshot: PendingInterruptSnapshot): Boolean {
        if (snapshot.requiresApproval) {
            return extractApproved(entry.payload) == true
        }
        return entry.payload != null
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractApproved(payload: Any?): Boolean? {
        if (payload == null) return null
        if (payload is Boolean) return payload
        if (payload is Map<*, *>) {
            return payload["approved"] as? Boolean
        }
        return null
    }

    private fun isResolved(entry: AguiResume): Boolean =
        RESOLVED_STATUS.equals(entry.status, ignoreCase = true)

    private fun fingerprint(entry: AguiResume): String {
        return try {
            objectMapper.writeValueAsString(
                mapOf(
                    "status" to entry.status,
                    "payload" to entry.payload,
                ),
            )
        } catch (_: Exception) {
            "${entry.status}:${entry.payload}"
        }
    }

    companion object {
        private const val RESOLVED_STATUS = "resolved"
    }
}
