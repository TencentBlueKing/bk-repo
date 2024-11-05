package com.tencent.bkrepo.job.batch.base

/**
 * [CompositeMongoDbBatchJob]的子任务
 */
@Suppress("TooManyFunctions")
open class ChildMongoDbBatchJob<T> {

    /**
     * 父任务启动回调
     */
    open fun onParentJobStart(context: ChildJobContext) {}

    /**
     * 执行子任务具体业务
     */
    open fun run(row: T, collectionName: String, context: JobContext) = Unit

    /**
     * 父任务结束回调
     */
    open fun onParentJobFinished(context: ChildJobContext) {}

    /**
     * 表执行结束回调
     * */
    open fun onRunCollectionFinished(collectionName: String, context: JobContext) {}

    /**
     * 创建子任务action
     *
     * @param parentJobContext 父任务上下文
     *
     * @return 子任务上下文
     */
    open fun createChildJobContext(parentJobContext: JobContext): ChildJobContext {
        return ChildJobContext(parentJobContext)
    }

    fun getJobName(): String = javaClass.simpleName
}
