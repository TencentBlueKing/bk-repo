package com.tencent.bkrepo.common.stream.binder.memory.queue

import org.springframework.messaging.Message

data class QueueItem(val message: Message<*>, val destination: String)
