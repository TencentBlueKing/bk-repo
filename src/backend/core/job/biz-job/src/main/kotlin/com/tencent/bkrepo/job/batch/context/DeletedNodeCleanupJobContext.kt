package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.job.batch.base.JobContext
import java.util.concurrent.atomic.AtomicLong

class DeletedNodeCleanupJobContext(
    val fileCount: AtomicLong = AtomicLong(),
    val folderCount: AtomicLong = AtomicLong(),
    ) : JobContext() {
    override fun toString(): String {
        return "Delete Node: file[$fileCount], folder[$folderCount], " +
                "total[${fileCount.toLong() + folderCount.toLong()}]"
    }
}
