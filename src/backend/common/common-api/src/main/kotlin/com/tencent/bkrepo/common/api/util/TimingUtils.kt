package com.tencent.bkrepo.common.api.util

inline fun <R> executeAndMeasureNanoTime(block: () -> R): Pair<R, Long> {
    val start = System.nanoTime()
    val result = block()
    return result to (System.nanoTime() - start)
}