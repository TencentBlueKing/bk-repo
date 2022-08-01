package com.tencent.bkrepo.common.api.stream

import java.io.InputStream
import java.util.concurrent.Future

interface ChunkedFuture<V, F> : Future<V> {

    fun getInputStream(v: V): InputStream
    fun getFuture(): F
}
