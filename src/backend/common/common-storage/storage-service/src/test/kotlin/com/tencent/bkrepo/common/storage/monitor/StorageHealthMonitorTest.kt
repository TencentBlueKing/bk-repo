package com.tencent.bkrepo.common.storage.monitor

import com.tencent.bkrepo.common.storage.config.UploadProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class StorageHealthMonitorTest {

    private val uploadProperties: UploadProperties = UploadProperties(location = "temp")
    private val storageCredentials = FileSystemCredentials(upload = uploadProperties)
    private val monitorConfig: MonitorProperties = MonitorProperties(
        enabled = true,
        fallbackLocation = "temp-fallback",
        interval = Duration.ofSeconds(5),
        timeout = Duration.ofSeconds(5),
        timesToRestore = 5,
        timesToFallback = 2
    )
    private val storageProperties: StorageProperties = StorageProperties(filesystem = storageCredentials, monitor = monitorConfig)

    @Test
    fun testCheck() {
        val monitor = StorageHealthMonitor(storageProperties)
        TimeUnit.SECONDS.sleep(10)
        monitor.stop()
    }

    @Test
    fun testRefresh() {
        val config = MonitorProperties(
            enabled = true,
            fallbackLocation = "temp-fallback",
            interval = Duration.ofSeconds(5),
            timeout = Duration.ofSeconds(10),
            timesToRestore = 5,
            timesToFallback = 2
        )
        val storageProperties = StorageProperties(filesystem = storageCredentials, monitor = config)
        val monitor = StorageHealthMonitor(storageProperties)
        repeat(2) {
            monitor.add(object : StorageHealthMonitor.Observer {
                override fun unhealthy(fallbackPath: Path?, reason: String?) {
                    println("unhealthy, fallbackPath: $fallbackPath, reason: $reason")
                }
                override fun restore(monitor: StorageHealthMonitor) {
                    println("restore")
                }
            })
        }
        // 4s, check 1 time
        TimeUnit.SECONDS.sleep(4)
        Assertions.assertTrue(monitor.health.get())

        println("Change to 1 nano")
        config.timeout = Duration.ofNanos(1)

        // 5s, check n time
        TimeUnit.SECONDS.sleep(2)
        // should change to unhealthy
        Assertions.assertFalse(monitor.health.get())

        println("Change to 10 second")
        config.timeout = Duration.ofSeconds(10)

        // 24s, check 4 time
        TimeUnit.SECONDS.sleep(24)
        // should keep unhealthy
        Assertions.assertFalse(monitor.health.get())

        // 24 + 2s, check 5 time
        TimeUnit.SECONDS.sleep(2)
        // should restore healthy
        Assertions.assertTrue(monitor.health.get())

        monitor.stop()
    }
}
