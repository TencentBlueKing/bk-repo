package com.tencent.bkrepo.job.batch.base

import java.util.concurrent.atomic.AtomicLong

/**
 * 任务上下文
 * */
open class JobContext(
    // 执行总数
    var total: AtomicLong = AtomicLong(),
    // 执行成功数
    var success: AtomicLong = AtomicLong(),
    // 执行失败数
    var failed: AtomicLong = AtomicLong()
) {
    override fun toString(): String {
        return "Success[$success], failed[$failed], total[$total]"
    }
}
