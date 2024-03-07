package com.tencent.bkrepo.archive.job.archive

import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class EmptyUtils : CompressionUtils {
    override fun compress(src: Path, target: Path): Mono<File> {
        Files.move(src, target)
        return Mono.just(target.toFile())
    }

    override fun uncompress(target: Path, src: Path): Mono<File> {
        Files.move(target, src)
        return Mono.just(src.toFile())
    }

    override fun getSuffix(): String = ""

    override fun name(): String = NAME

    companion object {
        const val NAME = "none"
    }
}
