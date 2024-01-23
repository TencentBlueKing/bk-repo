package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.archive.job.FileProvider
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.cleanup.BasedAtimeAndMTimeFileExpireResolver
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChecksumFileProvider(
    private val workDir: Path,
    private val fileProvider: FileProvider,
    private val cacheTime: Duration,
    private val executor: Executor,
) : FileProvider {

    private val fileExpireResolver = BasedAtimeAndMTimeFileExpireResolver(cacheTime)
    private val monitorExecutor = Executors.newSingleThreadScheduledExecutor()

    init {
        monitorExecutor.scheduleAtFixedRate(this::deleteAfterAccess, 0, 1, TimeUnit.HOURS)
    }

    override fun get(sha256: String, range: Range, storageCredentials: StorageCredentials): Mono<File> {
        check(range.isFullContent())
        val filePath = workDir.resolve("$sha256.checksum")
        if (Files.exists(filePath)) {
            return Mono.just(filePath.toFile())
        }
        return fileProvider.get(sha256, range, storageCredentials).flatMap {
            signFile(it, filePath, sha256, storageCredentials.key)
        }
    }

    private fun signFile(file: File, checksumFilePath: Path, sha256: String, key: String?): Mono<File> {
        return Mono.fromCallable {
            try {
                sign(file, checksumFilePath, sha256, key)
            } finally {
                file.delete()
            }
        }.publishOn(Schedulers.fromExecutor(executor))
    }

    private fun sign(file: File, checksumFilePath: Path, sha256: String, key: String?): File {
        synchronized(sha256.intern()) {
            Files.newOutputStream(checksumFilePath).use { out ->
                val bkSync = BkSync()
                val throughput = measureThroughput(file.length()) { bkSync.checksum(file, out) }
                logger.info("Success to sign file [$sha256] on $key, $throughput.")
            }
            checksumFilePath.toFile()
        }
        return checksumFilePath.toFile()
    }

    private fun deleteAfterAccess() {
        Files.list(workDir).use {
            it.forEach { path ->
                if (Files.isRegularFile(path) && fileExpireResolver.isExpired(path.toFile())) {
                    Files.deleteIfExists(path)
                    logger.info("Delete sign file ${path.toAbsolutePath()}.")
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChecksumFileProvider::class.java)
    }
}
