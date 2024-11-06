package com.tencent.bkrepo.job.batch.base

open class ChildJobContext(
    val parentContext: JobContext
) : JobContext()
