package com.tencent.bkrepo.common.stream.source

import org.springframework.cloud.stream.annotation.Output
import org.springframework.messaging.MessageChannel

interface EventSource {
    @Output(OUTPUT)
    fun output(): MessageChannel

    companion object {
        const val OUTPUT = "event-output"
    }
}
