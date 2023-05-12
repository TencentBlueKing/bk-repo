package com.tencent.bkrepo.job.batch.action

import com.tencent.bkrepo.job.batch.context.JobActionContext

interface JobAction<T> {
    fun start(): JobActionContext

    fun run(context: JobActionContext, node: T)

    fun finished(context: JobActionContext)

    fun name(): String
}
