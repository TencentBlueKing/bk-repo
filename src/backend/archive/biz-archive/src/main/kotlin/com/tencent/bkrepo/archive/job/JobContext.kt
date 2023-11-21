package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.common.api.util.HumanReadable
import java.util.concurrent.atomic.AtomicLong

data class JobContext(
    var success: AtomicLong = AtomicLong(),
    var failed: AtomicLong = AtomicLong(),
    var total: AtomicLong = AtomicLong(),
    var totalSize: AtomicLong = AtomicLong(),
) {
    fun reset() {
        success.set(0)
        failed.set(0)
        total.set(0)
        totalSize.set(0)
    }

    override fun toString(): String {
        return "success: $success, failed: $failed, total: $total, totalSize:${HumanReadable.size(totalSize.get())}"
    }
}
