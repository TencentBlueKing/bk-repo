package com.tencent.bkrepo.common.api.stream

import java.io.File
import java.io.InputStream
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

open class FileChunkedFutureWrapper(future: FutureTask<File>) :
    AbstractChunkedFutureWrapper<File>(future) {
    override fun getInputStream(): InputStream {
        return super.get().inputStream()
    }

    override fun getInputStream(timeout: Long, unit: TimeUnit): InputStream {
        return super.get(timeout, unit).inputStream()
    }
}
