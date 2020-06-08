package com.tencent.bkrepo.common.storage.monitor

import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class StorageHealthMonitorTest {

    private val config: MonitorProperties = MonitorProperties(
        enabled = true,
        fallbackLocation = "temp-fallback",
        interval = Duration.ofSeconds(5)
    )

    @Test
    fun testCheck() {
        val monitor = StorageHealthMonitor("temp", config)
        TimeUnit.SECONDS.sleep(10)
        monitor.stop()
    }

    @Test
    fun testRefresh() {
        val config = MonitorProperties(
            enabled = true,
            fallbackLocation = "temp-fallback",
            interval = Duration.ofSeconds(1),
            timeout = Duration.ofNanos(1),
            timesToRestore = 5
        )
        val monitor = StorageHealthMonitor("temp", config)
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
        TimeUnit.SECONDS.sleep(5)
        // should print unhealthy

        config.timeout = Duration.ofSeconds(1)
        println("Change to 1 second")

        TimeUnit.SECONDS.sleep(6)
        // should print restore
        monitor.stop()
    }

}