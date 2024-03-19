package com.tencent.bkrepo.archive.job

import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.function.Function

/**
 * 具有主动拉取功能的订阅者类
 */
class ActiveTaskSubscriber<T, R>(
    private val maxConcurrency: Int,
    interval: Duration,
    private val accept: Function<T, Mono<R>>,
) : BaseSubscriber<PriorityWrapper<T>>() {
    private var subscription: Subscription? = null
    private val semaphore = Semaphore(maxConcurrency)

    init {
        // 创建一个定时器，以设定的时间间隔向上游发送请求，只有在有许可证的情况下才会发起请求
        Flux.interval(interval)
            .subscribe {
                val permits = semaphore.availablePermits().toLong()
                if (permits > 0) {
                    subscription?.request(permits)
                }
            }
    }

    // 监听上游订阅的回调函数
    override fun hookOnSubscribe(subscription: Subscription) {
        this.subscription = subscription
        subscription.request(semaphore.availablePermits().toLong())
    }

    // 处理来自上游的数据
    override fun hookOnNext(value: PriorityWrapper<T>) {
        logger.info("Start execute task[${value.priority}]")
        // 尝试获取许可，成功则继续执行
        if (semaphore.tryAcquire()) {
            accept.apply(value.obj)
                .onErrorResume { Mono.empty() }
                .doFinally {
                    logger.info("Finish execute task[${value.priority}]")
                    semaphore.release()
                    subscription?.request(1)
                }
                .subscribe()
        } else {
            // 如果许可已被占用，则跳过该任务
            logger.warn("Can't acquire permit, skip task[${value.priority}]")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ActiveTaskSubscriber::class.java)
    }
}
