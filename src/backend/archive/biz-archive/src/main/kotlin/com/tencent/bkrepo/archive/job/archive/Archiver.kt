package com.tencent.bkrepo.archive.job.archive

import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Path

/**
 * 归档器
 * */
interface Archiver {
    /**
     * 压缩文件
     * */
    fun compress(src: Path, target: Path): Mono<File>

    /**
     * 解压文件
     * */
    fun uncompress(target: Path, src: Path): Mono<File>

    /**
     * 归档后文件的后缀
     * */
    fun getSuffix(): String

    /**
     * 归档器名称
     * */
    fun name(): String
}
