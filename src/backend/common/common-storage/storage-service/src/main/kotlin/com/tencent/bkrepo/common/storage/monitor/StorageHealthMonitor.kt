package com.tencent.bkrepo.common.storage.monitor

import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.util.toPath
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class StorageHealthMonitor(
    storageProperties: StorageProperties
) {
    var health: AtomicBoolean = AtomicBoolean(true)
    var reason: String? = null
    private val monitorConfig = storageProperties.monitor
    private val storageCredentials = storageProperties.defaultStorageCredentials()
    private val executorService = Executors.newSingleThreadExecutor()
    private val observerList = mutableListOf<Observer>()
    private var healthyThroughputCount: AtomicInteger = AtomicInteger(0)
    private var unhealthyThroughputCount: AtomicInteger = AtomicInteger(0)

    init {
        require(!monitorConfig.timeout.isNegative && !monitorConfig.timeout.isZero)
        require(!monitorConfig.interval.isNegative && !monitorConfig.interval.isZero)
        require(monitorConfig.timesToRestore > 0)
        Files.createDirectories(getPrimaryPath())
        monitorConfig.fallbackLocation?.let { Files.createDirectories(Paths.get(it)) }
        start()
        logger.info("Start up storage monitor for path[${storageCredentials.upload.location}]")
    }

    private fun start() {
        thread {
            while (true) {
                var sleep = true
                if (monitorConfig.enabled) {
                    val checker = StorageHealthChecker(getPrimaryPath(), monitorConfig.dataSize)
                    val future = executorService.submit(checker)
                    sleep = try {
                        future.get(monitorConfig.timeout.seconds, TimeUnit.SECONDS)
                        changeToHealthy()
                        true
                    } catch (timeoutException: TimeoutException) {
                        changeToUnhealthy(IO_TIMEOUT_MESSAGE)
                    } catch (exception: Exception) {
                        changeToUnhealthy(exception.message.orEmpty())
                    } finally {
                        checker.clean()
                    }
                }

                if (sleep) {
                    TimeUnit.SECONDS.sleep(monitorConfig.interval.seconds)
                }
            }
        }
    }

    fun stop() {
        monitorConfig.enabled = false
    }

    fun add(observer: Observer) {
        observerList.add(observer)
    }

    fun remove(observer: Observer?) {
        observerList.remove(observer)
    }

    fun getFallbackPath(): Path? = monitorConfig.fallbackLocation?.toPath()

    private fun getPrimaryPath(): Path = storageCredentials.upload.location.toPath()

    private fun changeToUnhealthy(message: String): Boolean {
        var sleep = true
        healthyThroughputCount.set(0)
        val count = unhealthyThroughputCount.incrementAndGet()
        if (health.get()) {
            // 如果当前是健康状态，不睡眠立即检查
            sleep = false
            logger.warn("Path[${getPrimaryPath()}] check failed [$count/${monitorConfig.timesToFallback}].")
        }

        if (count >= monitorConfig.timesToFallback) {
            if (health.compareAndSet(true, false)) {
                logger.error("Path[${getPrimaryPath()}] change to unhealthy, reason: $reason")
                reason = message
                for (observer in observerList) {
                    observer.unhealthy(getFallbackPath(), reason)
                }
                sleep = true
            }
        }
        return sleep
    }

    private fun changeToHealthy() {
        unhealthyThroughputCount.set(0)
        val count = healthyThroughputCount.incrementAndGet()
        if (!health.get()) {
            logger.warn("Try to restore [$count/${monitorConfig.timesToRestore}].")
        }

        if (count >= monitorConfig.timesToRestore) {
            if (health.compareAndSet(false, true)) {
                logger.info("Path[${getPrimaryPath()}] restore healthy.")
                for (observer in observerList) {
                    observer.restore(this)
                }
            }
        }
    }

    companion object {
        const val IO_TIMEOUT_MESSAGE = "IO Delay"
        private val logger = LoggerFactory.getLogger(StorageHealthMonitor::class.java)
    }

    interface Observer {
        fun unhealthy(fallbackPath: Path?, reason: String?)
        fun restore(monitor: StorageHealthMonitor) {}
    }
}
