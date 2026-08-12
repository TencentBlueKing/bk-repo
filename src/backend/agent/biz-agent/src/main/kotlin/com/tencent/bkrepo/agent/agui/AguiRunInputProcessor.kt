/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agui

import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.stereotype.Component

/** run 前统一处理 tools[] 与 resume[]。 */
@Component
class AguiRunInputProcessor(
    private val frontendToolSanitizer: FrontendToolSanitizer,
    private val aguiResumeValidator: AguiResumeValidator,
) {

    fun prepare(userId: String, projectId: String, input: RunAgentInput): RunAgentInput {
        aguiResumeValidator.validateAndPrepare(userId, projectId, input)
        return frontendToolSanitizer.sanitize(input)
    }
}
