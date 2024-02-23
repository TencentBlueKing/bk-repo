package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.archive.job.archive.DiskHealthObserver
import com.tencent.bkrepo.common.api.concurrent.ComparableFutureTask
import com.tencent.bkrepo.common.api.concurrent.PriorityCallableTask
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.util.StorageUtils
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.measureNanoTime

class FileStorageFileProvider(
    private val fileDir: Path,
    private val highWaterMark: Long,
    private val lowWaterMark: Long,
    private val executor: Executor,
    private val checkInterval: Long = CHECK_INTERVAL,
) : PriorityFileProvider, DiskHealthObserver {

    /**
     * 下载器的状态，true表示活跃，false表示不活跃，即暂时下载
     * */
    private var status = AtomicBoolean(true)
    private val lock = ReentrantLock()
    private val available = lock.newCondition()

    init {
        require(lowWaterMark < highWaterMark)
        Flux.interval(Duration.ofMillis(checkInterval))
            .map {
                val diskFreeInBytes = fileDir.toFile().usableSpace
                val threshold = if (status.get()) lowWaterMark else highWaterMark
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
        val filePath = fileDir.resolve(sha256)
        if (Files.exists(filePath)) {
            return Mono.just(filePath.toFile())
        }
        return Mono.create {
            val task = PriorityCallableTask<File>(priority) {
                try {
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
            check(range.length == file.length())
        }
        return file
    }

    override fun get(sha256: String, range: Range, storageCredentials: StorageCredentials): Mono<File> {
        return get(sha256, range, storageCredentials, Ordered.LOWEST_PRECEDENCE)
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
        if (!status.get()) {
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
        if (status.compareAndSet(false, true)) {
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
        if (status.compareAndSet(true, false)) {
            logger.info("FileProvider change to inactive.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileStorageFileProvider::class.java)
        private const val RETRY_TIMES = 3
        private const val CHECK_INTERVAL = 60000L
    }
}
