package com.tencent.bkrepo.common.api.stream

import java.io.File
import java.io.InputStream
import java.util.concurrent.FutureTask

class FileChunkedFutureWrapper(private val future: FutureTask<File>) : AbstractChunkedFutureWrapper<File>(future) {
    override fun getInputStream(v: File): InputStream {
        return future.get().inputStream()
    }
}
