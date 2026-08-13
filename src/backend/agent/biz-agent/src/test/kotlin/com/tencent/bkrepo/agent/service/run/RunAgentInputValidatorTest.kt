/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import io.agentscope.core.agui.model.RunAgentInput
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RunAgentInputValidatorTest {

    private val validator = RunAgentInputValidator(
        EffectiveAgentRuntimeProperties.defaults().copy(
            maxThreadIdLength = 128,
            maxMessageLength = 32_000,
        ),
    )

    @Test
    fun `rejects blank threadId`() {
        val input = RunAgentInput.builder()
            .threadId(" ")
            .runId("run-1")
            .build()
        assertThrows(ErrorCodeException::class.java) { validator.validate(input) }
    }

    @Test
    fun `rejects run without messages or resume`() {
        val input = RunAgentInput.builder()
            .threadId("thread-1")
            .runId("run-1")
            .build()
        assertThrows(ErrorCodeException::class.java) { validator.validate(input) }
    }
}
