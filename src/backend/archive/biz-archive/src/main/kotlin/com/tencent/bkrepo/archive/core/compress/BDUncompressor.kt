package com.tencent.bkrepo.archive.core.compress

import com.tencent.bkrepo.common.api.concurrent.PriorityRunnableWrapper
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
        return Mono.zip(bdFile, baseFile) { bd, bsf ->
            uncompress(bd, bsf, sha256, workDir)
        }.flatMap { it }
    }

    private fun uncompress(bdFile: File, baseFile: File, sha256: String, workDir: Path): Mono<File> {
        return Mono.create {
            val wrapper = PriorityRunnableWrapper(seq.getAndIncrement()) {
                try {
                    val start = System.nanoTime()
                    val file = BDUtils.patch(bdFile, baseFile, workDir)
                    val nanos = System.nanoTime() - start
                    val throughput = Throughput(file.length(), nanos)
                    logger.info("Success to bd uncompress $sha256,$throughput.")
                    it.success(file)
                } catch (e: Exception) {
                    logger.error("Failed to bd uncompress $sha256", e)
                    it.error(e)
                } finally {
                    bdFile.delete()
                    baseFile.delete()
                }
            }
            executor.execute(wrapper)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BDUncompressor::class.java)
    }
}
