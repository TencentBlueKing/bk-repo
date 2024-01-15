package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.common.bksync.file.BDUtils
import com.tencent.bkrepo.common.storage.monitor.Throughput
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executor

class BDUncompressor(
    private val executor: Executor,
) {

    /**
     * 根据源文件和签名文件，压缩成新的bd文件
     * */
    fun patch(bdFile: Mono<File>, baseFile: Mono<File>, sha256: String, workDir: Path): Mono<File> {
        return Mono.zip(bdFile, baseFile) { bd, bsf ->
            uncompress(bd, bsf, sha256, workDir)
        }.flatMap { it }
    }

    private fun uncompress(bdFile: File, baseFile: File, sha256: String, workDir: Path): Mono<File> {
        return Mono.fromCallable {
            try {
                val start = System.nanoTime()
                val file = BDUtils.patch(bdFile, baseFile, workDir)
                val nanos = System.nanoTime() - start
                val throughput = Throughput(nanos, file.length())
                logger.info("Success to bd uncompress $sha256,$throughput.")
                file
            } finally {
                bdFile.delete()
                baseFile.delete()
            }
        }.publishOn(Schedulers.fromExecutor(executor))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BDUncompressor::class.java)
    }
}
