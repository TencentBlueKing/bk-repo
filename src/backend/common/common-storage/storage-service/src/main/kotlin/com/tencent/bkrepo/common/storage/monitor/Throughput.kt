package com.tencent.bkrepo.common.storage.monitor

import com.tencent.bkrepo.common.api.util.HumanReadable
import java.time.Duration
import java.time.temporal.ChronoUnit

data class Throughput(
    val bytes: Long,
    val time: Long,
    val unit: ChronoUnit = ChronoUnit.NANOS
) {
    val duration: Duration = Duration.of(time, unit)

    override fun toString(): String {
        return with(HumanReadable) {
            "size: ${size(bytes)}, elapse: ${time(duration.toNanos())}, average: ${throughput(bytes, duration.toNanos())}"
        }
    }
}
