package com.tencent.bkrepo.common.storage.monitor

import com.tencent.bkrepo.common.api.util.toPath
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
    val uploadConfig: UploadProperties,
    val monitorConfig: MonitorProperties
) {
    var health: AtomicBoolean = AtomicBoolean(true)
    var reason: String? = null
    private val executorService = Executors.newSingleThreadExecutor()
    private val observerList = mutableListOf<Observer>()
    private var healthyThroughputCount: AtomicInteger = AtomicInteger(0)

    init {
        require(!monitorConfig.timeout.isNegative && !monitorConfig.timeout.isZero)
        require(!monitorConfig.interval.isNegative && !monitorConfig.interval.isZero)
        require(monitorConfig.timesToRestore > 0)
        Files.createDirectories(Paths.get(uploadConfig.location))
        monitorConfig.fallbackLocation?.let { Files.createDirectories(Paths.get(it)) }
        start()
        logger.info("Start up storage monitor for path[${uploadConfig.location}]")
    }

    private fun start() {
        thread {
            while (monitorConfig.enabled) {
                val checker = StorageHealthChecker(Paths.get(uploadConfig.location), monitorConfig.dataSize)
                val future = executorService.submit(checker)
                try {
                    future.get(monitorConfig.timeout.seconds, TimeUnit.SECONDS)
                } catch (timeoutException: TimeoutException) {
                    changeToUnhealthy(IO_TIMEOUT_MESSAGE)
                } catch (exception: Exception) {
                    changeToUnhealthy(exception.message.orEmpty())
                }
                changeToHealthy()
                TimeUnit.SECONDS.sleep(monitorConfig.interval.seconds)
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

    fun getPrimaryPath(): Path = uploadConfig.location.toPath()

    fun getFallbackPath(): Path? = monitorConfig.fallbackLocation?.toPath()

    private fun changeToUnhealthy(message: String) {
        healthyThroughputCount.set(0)
        reason = message
        if (health.compareAndSet(true, false)) {
            logger.error("Path[${getPrimaryPath()}] is unhealthy, reason: $reason")
            for (observer in observerList) {
                observer.unhealthy(getFallbackPath(), reason)
            }
        }
    }

    private fun changeToHealthy() {
        val count = healthyThroughputCount.incrementAndGet()
        if (count > monitorConfig.timesToRestore) {
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
