package com.tencent.bkrepo.monitor.notify

import reactor.core.publisher.Mono

interface MessageNotifier {
    fun notifyMessage(content: Any): Mono<Void>
}
