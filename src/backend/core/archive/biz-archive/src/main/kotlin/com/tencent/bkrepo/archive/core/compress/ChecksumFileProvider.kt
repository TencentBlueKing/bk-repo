package com.tencent.bkrepo.archive.core.compress

import com.tencent.bkrepo.archive.core.provider.FileProvider
import com.tencent.bkrepo.archive.core.provider.FileProviderProxy
import com.tencent.bkrepo.archive.core.mapPriority
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class ChecksumFileProvider<T>(
    private val fileProvider: FileProvider<T>,
    private val executor: Executor,
    private val signPath: Path,
) : FileProviderProxy<T>(fileProvider) {

    private val seq = AtomicInteger(0)

    override fun get(param: T): Mono<File> {
        val key = key(param)
        val checksumFilePath = signPath.resolve(key)
        val tempFilePath = signPath.resolve(key.plus(".tmp"))
        return fileProvider.get(param).mapPriority(executor, seq.getAndIncrement()) {
            val throughput = measureThroughput(it.length()) {
                sign(tempFilePath, it)
            }
            Files.move(tempFilePath, checksumFilePath)
            logger.info("Success generate sign file $checksumFilePath,$throughput.")
            checksumFilePath.toFile()
        }
    }

    private fun sign(dst: Path, src: File) {
        try {
            Files.newOutputStream(dst).use { out ->
                val bkSync = BkSync()
                bkSync.checksum(src, out)
            }
        } finally {
            src.delete()
        }
    }

    override fun key(param: T): String {
        return fileProvider.key(param).plus(".checksum")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChecksumFileProvider::class.java)
    }
}
