package com.tencent.bkrepo.common.api.stream

import java.util.concurrent.Future

interface ChunkedFutureListener<T> {
    fun done(future: Future<T>?, getInputStreamTime: Long)
}
