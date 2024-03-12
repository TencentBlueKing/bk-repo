package com.tencent.bkrepo.archive.job.compress

import com.tencent.bkrepo.archive.job.FileProvider
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.cleanup.BasedAtimeAndMTimeFileExpireResolver
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import org.reactivestreams.Subscriber
import org.slf4j.LoggerFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ChecksumFileProvider(
    private val workDir: Path,
    private val fileProvider: FileProvider,
    private val cacheTime: Duration,
    private val executor: Executor,
) : FileProvider {

    private val fileExpireResolver = BasedAtimeAndMTimeFileExpireResolver(cacheTime)
    private val monitorExecutor = Executors.newSingleThreadScheduledExecutor()

    private val signFileListeners = ConcurrentHashMap<String, BlockingQueue<Subscriber<File>>>()

    init {
        monitorExecutor.scheduleAtFixedRate(this::deleteAfterAccess, 0, 1, TimeUnit.HOURS)
    }

    override fun get(sha256: String, range: Range, storageCredentials: StorageCredentials): Mono<File> {
        check(range.isFullContent())
        val filePath = workDir.resolve("$sha256.checksum")
        if (Files.exists(filePath)) {
            return Mono.just(filePath.toFile())
        }
        val listeners = signFileListeners.getOrPut(sha256) {
            synchronized(sha256.intern()) {
                signFileListeners.getOrPut(sha256) {
                    initSign(sha256, range, storageCredentials, filePath)
                }
            }
        }
        return Mono.create {
            val subscriber = object : BaseSubscriber<File>() {
                override fun hookOnNext(value: File) {
                    it.success(value)
                }

                override fun hookOnError(throwable: Throwable) {
                    it.error(throwable)
                }
            }
            listeners.add(subscriber)
        }
    }

    private fun signFile(file: File, checksumFilePath: Path, sha256: String, key: String?): Mono<File> {
        return Mono.fromCallable {
            try {
                Files.newOutputStream(checksumFilePath).use { out ->
                    val bkSync = BkSync()
                    val throughput = measureThroughput(file.length()) { bkSync.checksum(file, out) }
                    logger.info("Success to sign file [$sha256] on $key, $throughput.")
                }
                checksumFilePath.toFile()
            } finally {
                file.delete()
            }
        }.publishOn(Schedulers.fromExecutor(executor))
    }

    private fun initSign(
        sha256: String,
        range: Range,
        storageCredentials: StorageCredentials,
        filePath: Path,
    ): BlockingQueue<Subscriber<File>> {
        logger.info("Init sign for file[$sha256]")
        val listeners = LinkedBlockingQueue<Subscriber<File>>()
        val subscriber = object : BaseSubscriber<File>() {
            override fun hookOnNext(value: File) {
                // 提前移除签名队列，防止拿到旧的队列
                signFileListeners.remove(sha256)
                listeners.forEach { it.onNext(value) }
            }

            override fun hookOnError(throwable: Throwable) {
                listeners.forEach { it.onError(throwable) }
            }
        }
        fileProvider.get(sha256, range, storageCredentials).flatMap {
            signFile(it, filePath, sha256, storageCredentials.key)
        }.subscribe(subscriber)
        return listeners
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
