package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.util.StorageUtils
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlin.system.measureNanoTime

class FileStorageFileProvider(
    private val fileDir: Path,
    private val diskFreeThreshold: Long,
    private val executor: Executor,
) : FileProvider {

    override fun get(sha256: String, range: Range, storageCredentials: StorageCredentials): Mono<File> {
        val filePath = fileDir.resolve(sha256)
        if (Files.exists(filePath)) {
            return Mono.just(filePath.toFile())
        }
        return Mono.fromCallable {
            logger.info("Downloading $sha256 on ${storageCredentials.key}")
            if (!Files.exists(filePath)) {
                download(sha256, range, storageCredentials, filePath)
            }
            val file = filePath.toFile()
            if (range != Range.FULL_RANGE) {
                check(range.length == file.length())
            }
            file
        }.publishOn(Schedulers.fromExecutor(executor))
    }

    private fun download(sha256: String, range: Range, storageCredentials: StorageCredentials, filePath: Path) {
        retry(RETRY_TIMES) {
            checkDiskSpace()
            val nanos = measureNanoTime {
                StorageUtils.downloadUseLocalPath(sha256, range, storageCredentials, filePath)
            }
            val throughput = Throughput(Files.size(filePath), nanos)
            logger.info("Success to download file [$sha256] on ${storageCredentials.key}, $throughput.")
        }
    }

    private fun checkDiskSpace() {
        var diskFreeInBytes = fileDir.toFile().usableSpace
        while (diskFreeInBytes < diskFreeThreshold) {
            logger.info(
                "DFree disk space below threshold.Available:" +
                    " $diskFreeInBytes bytes (threshold: $diskFreeThreshold).",
            )
            Thread.sleep(CHECK_INTERVAL)
            diskFreeInBytes = fileDir.toFile().usableSpace
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileStorageFileProvider::class.java)
        private const val RETRY_TIMES = 3
        private const val CHECK_INTERVAL = 60000L
    }
}
