package com.tencent.bkrepo.common.api.stream

import java.io.InputStream
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

interface ChunkedFuture<V> : Future<V> {

    fun getInputStream(): InputStream
    fun getInputStream(timeout: Long, unit: TimeUnit): InputStream
}
