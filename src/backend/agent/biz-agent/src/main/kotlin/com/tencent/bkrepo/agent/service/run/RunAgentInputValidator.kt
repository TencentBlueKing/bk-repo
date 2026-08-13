/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.common.api.util.Preconditions
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.stereotype.Component

/** AG-UI [RunAgentInput] 入参校验。 */
@Component
class RunAgentInputValidator(
    private val properties: EffectiveAgentRuntimeProperties,
) {

    fun validate(input: RunAgentInput) {
        Preconditions.checkNotBlank(input.threadId, "threadId")
        Preconditions.checkArgument(input.threadId.length <= properties.maxThreadIdLength, "threadId")
        Preconditions.checkNotBlank(input.runId, "runId")
        Preconditions.checkArgument(input.hasMessages() || input.hasResume(), "messages or resume")
        validateLatestUserMessage(input)
    }

    private fun validateLatestUserMessage(input: RunAgentInput) {
        if (!input.hasMessages()) return
        val latestUserText = input.messages
            .asReversed()
            .firstOrNull { "user".equals(it.role, ignoreCase = true) }
            ?.textContent
            ?: ""
        if (latestUserText.isNotBlank()) {
            Preconditions.checkArgument(latestUserText.length <= properties.maxMessageLength, "messages")
        }
    }
}
