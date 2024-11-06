package com.tencent.bkrepo.archive.core.compress

import com.tencent.bkrepo.archive.core.mapPriority
import com.tencent.bkrepo.common.bksync.file.BDUtils
import com.tencent.bkrepo.common.storage.monitor.Throughput
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class BDUncompressor(
    private val executor: Executor,
) {

    private val seq = AtomicInteger(Int.MIN_VALUE)

    /**
     * 根据源文件和签名文件，压缩成新的bd文件
     * */
    fun patch(bdFile: Mono<File>, baseFile: Mono<File>, sha256: String, workDir: Path): Mono<File> {
        return Mono.zip(baseFile, bdFile).mapPriority(executor, seq.getAndIncrement()) {
            uncompress(it.t1, it.t2, sha256, workDir)
        }
    }

    private fun uncompress(baseFile: File, bdFile: File, sha256: String, workDir: Path): File {
        try {
            val start = System.nanoTime()
            val file = BDUtils.patch(bdFile, baseFile, workDir)
            val nanos = System.nanoTime() - start
            val throughput = Throughput(file.length(), nanos)
            logger.info("Success to bd uncompress $sha256,$throughput.")
            return file
        } finally {
            bdFile.delete()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BDUncompressor::class.java)
    }
}
