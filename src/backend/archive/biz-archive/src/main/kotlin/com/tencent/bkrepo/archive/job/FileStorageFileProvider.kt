package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.common.api.constant.retry
import com.tencent.bkrepo.common.api.concurrent.ComparableFutureTask
import com.tencent.bkrepo.common.api.concurrent.PriorityCallableTask
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.util.StorageUtils
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.measureNanoTime

class FileStorageFileProvider(
    private val fileDir: Path,
    private val highWaterMark: Long,
    private val lowWaterMark: Long,
    private val executor: Executor,
    private val checkInterval: Duration,
) : PriorityFileProvider, DiskHealthObserver {

    /**
     * 下载器的状态，true表示活跃，false表示不活跃，即暂时下载
     * */
    private var activeStatus = AtomicBoolean(true)
    private val lock = ReentrantLock()
    private val available = lock.newCondition()
    private val seq = AtomicInteger()

    init {
        require(lowWaterMark < highWaterMark)
        if (!Files.exists(fileDir)) {
            Files.createDirectories(fileDir)
        }
        Flux.interval(checkInterval)
            .map {
                val diskFreeInBytes = fileDir.toFile().usableSpace
                val threshold = if (activeStatus.get()) lowWaterMark else highWaterMark
                val result = diskFreeInBytes < threshold
                if (result) {
                    logger.info(
                        "Free disk space below threshold. Available: " +
                            "$diskFreeInBytes bytes (threshold: $threshold).",
                    )
                }
                result
            }
            .subscribe {
                if (it) {
                    this.unHealthy()
                } else {
                    this.healthy()
                }
            }
    }

    override fun get(sha256: String, range: Range, storageCredentials: StorageCredentials, priority: Int): Mono<File> {
        logger.info("Prepare[$priority] download $sha256 on ${storageCredentials.key}")
        val filePath = fileDir.resolve(sha256)
        if (Files.exists(filePath)) {
            return Mono.just(filePath.toFile())
        }
        return Mono.create {
            val task = PriorityCallableTask<File>(priority) {
                try {
                    logger.info("Start run task[$priority].")
                    val file = doDownload(sha256, storageCredentials, filePath, range)
                    it.success(file)
                    file
                } catch (e: Exception) {
                    it.error(e)
                    throw e
                }
            }
            val futureTask = ComparableFutureTask(task)
            executor.execute(futureTask)
        }
    }

    private fun doDownload(
        sha256: String,
        storageCredentials: StorageCredentials,
        filePath: Path,
        range: Range,
    ): File {
        logger.info("Downloading $sha256 on ${storageCredentials.key}")
        if (!Files.exists(filePath)) {
            download(sha256, range, storageCredentials, filePath)
        }
        val file = filePath.toFile()
        if (range != Range.FULL_RANGE) {
            val length = file.length()
            check(range.length == length) { "File[$filePath] broken,require ${range.length},but actual $length." }
        }
        return file
    }

    override fun get(sha256: String, range: Range, storageCredentials: StorageCredentials): Mono<File> {
        return get(sha256, range, storageCredentials, seq.getAndIncrement())
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
        if (!activeStatus.get()) {
            lock.lock()
            try {
                logger.info("Pause download")
                available.await()
                logger.info("Continue download")
            } finally {
                lock.unlock()
            }
        }
    }

    override fun healthy() {
        if (activeStatus.compareAndSet(false, true)) {
            lock.lock()
            try {
                available.signalAll()
            } finally {
                lock.unlock()
            }
            logger.info("FileProvider change to active.")
        }
    }

    override fun unHealthy() {
        if (activeStatus.compareAndSet(true, false)) {
            logger.info("FileProvider change to inactive.")
        }
    }

    override fun isActive(): Boolean {
        return activeStatus.get()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileStorageFileProvider::class.java)
        private const val RETRY_TIMES = 3
    }
}
