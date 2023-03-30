package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.job.batch.base.JobContext
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

class DeletedNodeCleanupJobContext(
    val fileCount: AtomicLong = AtomicLong(),
    val folderCount: AtomicLong = AtomicLong(),
    val repoDeleteCount: AtomicLong = AtomicLong(),
    val expireDate: LocalDateTime
) : JobContext() {
    override fun toString(): String {
        return "Cleanup Repo: ${super.toString()}, delete[$repoDeleteCount]. " +
                "Delete Node: file[$fileCount], folder[$folderCount], " +
                "total[${fileCount.toLong() + folderCount.toLong()}]"
    }
}
