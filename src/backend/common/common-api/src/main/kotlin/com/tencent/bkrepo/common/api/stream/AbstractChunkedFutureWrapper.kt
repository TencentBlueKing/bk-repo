package com.tencent.bkrepo.common.api.stream

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

abstract class AbstractChunkedFutureWrapper<V>(private val future: Future<V>) : ChunkedFuture<V> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return future.cancel(mayInterruptIfRunning)
    }

    override fun isCancelled(): Boolean {
        return future.isCancelled
    }

    override fun isDone(): Boolean {
        return future.isDone
    }

    override fun get(): V {
        return future.get()
    }

    override fun get(timeout: Long, unit: TimeUnit): V {
        return future.get(timeout, unit)
    }
}
