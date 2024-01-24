package com.tencent.bkrepo.archive.job

import com.tencent.bkrepo.common.api.util.HumanReadable
import java.util.concurrent.atomic.AtomicLong

/**
 * 任务上下文
 * */
data class JobContext(
    /**
     * 任务成功数
     * */
    var success: AtomicLong = AtomicLong(),
    /**
     * 任务失败数
     * */
    var failed: AtomicLong = AtomicLong(),
    /**
     * 任务总数
     * */
    var total: AtomicLong = AtomicLong(),
    /**
     * 任务处理总大小
     * */
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
