package com.tencent.bkrepo.job.batch.base

import java.util.concurrent.atomic.AtomicLong

class FileJobContext(
    // 文件丢失数
    var fileMissing: AtomicLong = AtomicLong()
) : JobContext() {
    override fun toString(): String {
        return "${super.toString()},fileMissing[$fileMissing]"
    }
}
