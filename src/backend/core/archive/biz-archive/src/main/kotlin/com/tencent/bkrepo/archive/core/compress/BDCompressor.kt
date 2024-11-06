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

class BDCompressor(
    private val ratio: Float,
    private val executor: Executor,
    private val bigFileCompressPool: Executor,
    private val bigChecksumFileThreshold: Long,
) {
    private val seq = AtomicInteger(0)

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
        return Mono.zip(checksumFile, srcFile).mapPriority(executor, seq.getAndIncrement()) {
            compress(it.t2, it.t1, srcKey, destKey, workDir)
        }
    }

    private fun compress(src: File, checksum: File, srcKey: String, destKey: String, workDir: Path): File {
        try {
            val start = System.nanoTime()
            val file = BDUtils.deltaByChecksumFile(src, checksum, srcKey, destKey, workDir, ratio)
            val nanos = System.nanoTime() - start
            val throughput = Throughput(src.length(), nanos)
            logger.info("Success to bd compress $srcKey,$throughput.")
            return file
        } finally {
            src.delete()
        }
    }

    private fun chooseScheduler(size: Long): Executor {
        return if (size > bigChecksumFileThreshold) bigFileCompressPool else executor
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BDCompressor::class.java)
    }
}
