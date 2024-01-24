package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.common.bksync.file.BDUtils
import com.tencent.bkrepo.common.storage.monitor.Throughput
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executor

class BDCompressor(
    private val ratio: Float,
    private val executor: Executor,
) {

    /**
     * 根据源文件和签名文件，压缩成新的bd文件
     * */
    fun compress(
        srcFile: Mono<File>,
        checksumFile: Mono<File>,
        srcKey: String,
        destKey: String,
        workDir: Path,
    ): Mono<File> {
        return Mono.zip(checksumFile, srcFile) { checksum, src ->
            compress(src, checksum, srcKey, destKey, workDir)
        }.flatMap { it }
    }

    private fun compress(src: File, checksum: File, srcKey: String, destKey: String, workDir: Path): Mono<File> {
        return Mono.fromCallable {
            try {
                val start = System.nanoTime()
                val file = BDUtils.deltaByChecksumFile(src, checksum, srcKey, destKey, workDir, ratio)
                val nanos = System.nanoTime() - start
                val throughput = Throughput(nanos, file.length())
                logger.info("Success to bd compress $srcKey,$throughput.")
                file
            } finally {
                src.delete()
            }
        }.publishOn(Schedulers.fromExecutor(executor))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BDCompressor::class.java)
    }
}
