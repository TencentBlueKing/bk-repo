package com.tencent.bkrepo.archive.job

import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

class ActivelyPullSubscriber<T>(
    private val manager: BufferedResourceManager<T>,
    interval: Duration,
) : BaseSubscriber<T>() {
    private var subscription: Subscription? = null

    init {
        Flux.interval(interval).subscribe {
            val capacity = manager.remainingCapacity().toLong()
            if (capacity > 0) {
                logger.info("Pull $capacity items.")
                subscription?.request(capacity)
            }
        }
    }

    override fun hookOnSubscribe(subscription: Subscription) {
        this.subscription = subscription
        super.hookOnSubscribe(subscription)
    }

    override fun hookOnNext(value: T) {
        manager.process(value)
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ActivelyPullSubscriber::class.java)
    }
}
