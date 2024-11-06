package com.tencent.bkrepo.archive.core.provider

import reactor.core.publisher.Mono
import java.io.File

interface FileProvider<T> {
    fun get(param: T): Mono<File>
    fun key(param: T): String
    fun isActive(): Boolean = true
}
