package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.job.batch.action.JobAction
import com.tencent.bkrepo.job.batch.context.JobActionContext
import com.tencent.bkrepo.job.config.properties.MongodbJobProperties

abstract class CompositeMongoDbBatchJob<T>(
    properties: MongodbJobProperties
) : MongoDbBatchJob<T, JobContext>(properties) {
    override fun run(row: T, collectionName: String, context: JobContext) {
        val actions = actions()
        checkDuplicateAction(actions)
        val contextMap = HashMap<String, JobActionContext>()

        // TODO 处理异常情况
        // start
        actions.forEach { contextMap[it.name()] = it.start() }

        // stat
        actions.forEach { it.run(contextMap[it.name()]!!, row) }

        // finished
        actions.forEach { it.finished(contextMap[it.name()]!!) }
    }

    override fun createJobContext(): JobContext = JobContext()

    abstract fun actions(): List<JobAction<T>>

    private fun checkDuplicateAction(actions: List<JobAction<T>>) {
        val names = HashSet<String>()
        actions.forEach {
            check(!names.contains(it.name())) { "duplicate action name[${it.name()}]" }
            names.add(it.name())
        }
    }
}
