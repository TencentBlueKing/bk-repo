package com.tencent.bkrepo.archive.core.provider

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import java.util.concurrent.atomic.AtomicInteger

data class FileTask(
    val sha256: String,
    val range: Range,
    val storageCredentials: StorageCredentials,
    val priority: Int = seq.getAndIncrement(),
) {
    companion object {
        private val seq = AtomicInteger()
    }
}
