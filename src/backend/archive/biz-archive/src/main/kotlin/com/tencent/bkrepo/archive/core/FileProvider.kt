package com.tencent.bkrepo.archive.core

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import reactor.core.publisher.Mono
import java.io.File

interface FileProvider {
    fun get(sha256: String, range: Range, storageCredentials: StorageCredentials): Mono<File>
    fun isActive(): Boolean = true
}
