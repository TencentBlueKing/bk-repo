package com.tencent.bkrepo.job.batch.base

open class CompositeJobContext<T>(
    val childJobs: List<ChildMongoDbBatchJob<T>>,
) : JobContext() {
    private val childContextMap: Map<String, ChildJobContext>

    init {
        val m = HashMap<String, ChildJobContext>()
        childJobs.forEach {
            val childJobName = it.getJobName()
            check(!m.contains(childJobName)) { "duplicate child job name[${it.getJobName()}]" }
            m[childJobName] = it.createChildJobContext(this)
        }
        childContextMap = m
    }

    fun childContext(childJobName: String): ChildJobContext {
        return checkNotNull(childContextMap[childJobName]) { "child job[$childJobName] context not found" }
    }
}
