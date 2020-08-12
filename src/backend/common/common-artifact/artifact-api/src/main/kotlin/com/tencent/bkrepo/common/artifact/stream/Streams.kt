package com.tencent.bkrepo.common.artifact.stream

import java.io.Closeable
import java.nio.channels.FileLock

fun Closeable.closeQuietly() {
    try {
        this.close()
    } catch (ignored: Throwable) {
    }
}

fun FileLock.releaseQuietly() {
    try {
        if (this.isValid) {
            this.release()
        }
    } catch (ignored: Throwable) {
    }
}
