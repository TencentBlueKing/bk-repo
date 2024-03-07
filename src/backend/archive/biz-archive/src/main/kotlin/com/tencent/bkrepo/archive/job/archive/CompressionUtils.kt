package com.tencent.bkrepo.archive.job.archive

import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Path

interface CompressionUtils {
    fun compress(src: Path, target: Path): Mono<File>
    fun uncompress(target: Path, src: Path): Mono<File>
    fun getSuffix(): String

    fun name(): String
}
