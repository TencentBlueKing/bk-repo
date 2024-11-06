package com.tencent.bkrepo.archive.core.archive

import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * 空归档器，不进行任何操作，仅移动文件
 * */
class EmptyArchiver : Archiver {
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
