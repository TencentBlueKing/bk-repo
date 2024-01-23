package com.tencent.bkrepo.job.batch.base

/**
 * 可恢复的mongodb job上下文
 * */
open class RecoverableMongodbJobContext(
    val undoCollectionNames: MutableList<String>,
) : JobContext() {
    fun init(jobContext: JobContext) {
        success = jobContext.success
        failed = jobContext.failed
        total = jobContext.total
    }

    fun reset() {
        undoCollectionNames.clear()
        success.set(0)
        failed.set(0)
        total.set(0)
    }
}
