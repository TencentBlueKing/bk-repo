package com.tencent.bkrepo.common.stream.sink

import org.springframework.cloud.stream.annotation.Input
import org.springframework.messaging.SubscribableChannel

interface EventSink {
    @Input(INPUT)
    fun input(): SubscribableChannel

    companion object {
        const val INPUT = "event-input"
    }
}
