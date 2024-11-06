package com.tencent.bkrepo.archive.core

import com.tencent.bkrepo.common.api.concurrent.PriorityRunnableWrapper
import reactor.core.publisher.Mono
import java.util.concurrent.Executor

fun <T, R> Mono<T>.mapPriority(executor: Executor, seq: Int, mapping: (t: T) -> R): Mono<R> {
    return this.flatMap {
        createPriorityMono(executor, seq) { mapping(it) }
    }
}

fun <R> createPriorityMono(executor: Executor, seq: Int, mapping: () -> R): Mono<R> {
    return Mono.create { sink ->
        val wrapper = PriorityRunnableWrapper(seq) {
            try {
                sink.success(mapping())
            } catch (e: Exception) {
                sink.error(e)
            }
        }
        executor.execute(wrapper)
    }
}
