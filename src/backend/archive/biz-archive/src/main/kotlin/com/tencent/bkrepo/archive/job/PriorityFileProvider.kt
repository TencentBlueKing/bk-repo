package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import reactor.core.publisher.Mono
import java.io.File

/**
 * 优先级文件生产者
 * */
interface PriorityFileProvider : FileProvider {
    fun get(sha256: String, range: Range, storageCredentials: StorageCredentials, priority: Int): Mono<File>
}
