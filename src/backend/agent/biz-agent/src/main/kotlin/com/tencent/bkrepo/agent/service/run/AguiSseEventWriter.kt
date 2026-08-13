/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import io.agentscope.core.agui.encoder.AguiEventEncoder
import io.agentscope.core.agui.event.AguiEvent
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** AG-UI 事件 JSON 编码并通过 SSE 推送。 */
@Component
class AguiSseEventWriter {

    private val aguiEventEncoder = AguiEventEncoder()

    fun encode(event: AguiEvent): String = aguiEventEncoder.encodeToJson(event)

    fun send(emitter: SseEmitter, event: AguiEvent) {
        sendJson(emitter, encode(event))
    }

    fun sendJson(emitter: SseEmitter, eventJson: String) {
        emitter.send(
            SseEmitter.event()
                .data(eventJson, MediaType.APPLICATION_JSON),
        )
    }
}
