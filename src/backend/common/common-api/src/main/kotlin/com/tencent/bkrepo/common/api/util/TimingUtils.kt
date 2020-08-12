package com.tencent.bkrepo.common.api.util

import java.time.Duration

inline fun <R> executeAndMeasureNanoTime(block: () -> R): Pair<R, Long> {
    val start = System.nanoTime()
    val result = block()
    return result to (System.nanoTime() - start)
}

inline fun <R> executeAndMeasureTime(block: () -> R): Pair<R, Duration> {
    val start = System.nanoTime()
    val result = block()
    val durationNanoTime = System.nanoTime() - start
    return result to Duration.ofNanos(durationNanoTime)
}
