package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.config.properties.CompositeJobProperties
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration

/**
 * [CompositeMongoDbBatchJob]的子任务
 */
@Suppress("TooManyFunctions")
abstract class ChildMongoDbBatchJob<T>(
    properties: CompositeJobProperties
) : MongoDbBatchJob<T, JobContext>(properties) {

    /**
     * 父任务启动回调
     */
    open fun onParentJobStart(context: ChildJobContext) {}

    /**
     * 执行子任务具体业务
     */
    override fun run(row: T, collectionName: String, context: JobContext) = Unit

    /**
     * 父任务结束回调
     */
    open fun onParentJobFinished(context: ChildJobContext) {}

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

    override fun doStart0(jobContext: JobContext) {
        throw UnsupportedOperationException()
    }

    override fun collectionNames(): List<String> {
        throw UnsupportedOperationException()
    }

    override fun buildQuery(): Query {
        throw UnsupportedOperationException()
    }

    override fun mapToEntity(row: Map<String, Any?>): T {
        throw UnsupportedOperationException()
    }

    override fun entityClass(): Class<T> {
        throw UnsupportedOperationException()
    }

    override fun createJobContext(): JobContext {
        throw UnsupportedOperationException()
    }

    override fun shouldExecute(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun getLockName(): String {
        throw UnsupportedOperationException()
    }

    override fun getLockAtLeastFor(): Duration {
        throw UnsupportedOperationException()
    }

    override fun getLockAtMostFor(): Duration {
        throw UnsupportedOperationException()
    }

    override fun report(jobContext: JobContext) {
        throw UnsupportedOperationException()
    }

    override fun start(): Boolean {
        throw UnsupportedOperationException()
    }
}
