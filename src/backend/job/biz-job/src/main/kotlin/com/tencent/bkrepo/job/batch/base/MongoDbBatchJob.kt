/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.base

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.config.properties.MongodbJobProperties
import com.tencent.bkrepo.job.executor.BlockThreadPoolTaskExecutorDecorator
import com.tencent.bkrepo.job.executor.IdentityTask
import com.tencent.bkrepo.job.pojo.TJobFailover
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.net.InetAddress
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.system.measureNanoTime

/**
 * MongoDb抽象批处理作业Job
 * */
abstract class MongoDbBatchJob<Entity : Any, Context : JobContext>(
    private val properties: MongodbJobProperties,
) : MongodbFailoverJob<Context>(properties) {
    /**
     * 需要操作的表名列表
     * */
    abstract fun collectionNames(): List<String>

    /**
     * 需要处理数据的查询语句
     * */
    abstract fun buildQuery(): Query

    /**
     * 处理单条数据函数
     * @param row 单个数据
     * @param collectionName 当前数据所在表
     * */
    abstract fun run(row: Entity, collectionName: String, context: Context)

    /**
     * 将map对象化
     * @param row 查询返回的表单个数据map
     * */
    abstract fun mapToEntity(row: Map<String, Any?>): Entity

    abstract fun entityClass(): KClass<Entity>

    /**
     * 表执行结束回调
     * */
    open fun onRunCollectionFinished(collectionName: String, context: Context) {}

    private val batchSize: Int
        get() = properties.batchSize

    private val concurrentLevel: JobConcurrentLevel
        get() = properties.concurrentLevel

    private val permitsPerSecond: Double
        get() = properties.permitsPerSecond

    @Autowired
    private lateinit var lockingTaskExecutor: LockingTaskExecutor

    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    /**
     * job批处理执行器
     * */
    @Autowired
    private lateinit var executor: BlockThreadPoolTaskExecutorDecorator

    /**
     * 是否存在异步任务
     * */
    private var hasAsyncTask = false

    /**
     * 未执行列表
     * */
    private var undoList = Collections.synchronizedList(mutableListOf<String>())

    /**
     * 恢复任务上下文
     * */
    private var recoverableJobContext = RecoverableMongodbJobContext(mutableListOf())

    /**
     * 是否是从故障中恢复
     * */
    private var recover = false

    override fun doStart0(jobContext: Context) {
        try {
            hasAsyncTask = false
            prepareContext(jobContext)
            val collectionNames = undoList.toList()
            if (concurrentLevel == JobConcurrentLevel.COLLECTION) {
                // 使用闭锁来保证表异步生产任务的结束
                val countDownLatch = CountDownLatch(collectionNames.size)
                runAsync(collectionNames, true) {
                    runCollection(it, jobContext)
                    countDownLatch.countDown()
                }
                countDownLatch.await()
            } else {
                collectionNames.forEach {
                    runCollection(it, jobContext)
                }
            }
        } finally {
            if (hasAsyncTask && concurrentLevel != JobConcurrentLevel.SERIALIZE) {
                executor.completeAndGet(taskId, WAIT_TIMEOUT)
            }
        }
    }

    /**
     * 准备执行上下文
     * */
    private fun prepareContext(jobContext: Context) {
        undoList.clear()
        if (recover) {
            jobContext.success = recoverableJobContext.success
            jobContext.failed = recoverableJobContext.failed
            jobContext.total = recoverableJobContext.total
            undoList.addAll(recoverableJobContext.undoCollectionNames)
            recover = false
        } else {
            recoverableJobContext.init(jobContext)
            undoList.addAll(collectionNames())
        }
    }

    /**
     * 处理单个表数据
     * */
    private fun runCollection(collectionName: String, context: Context) {
        if (!shouldRun()) {
            logger.info("Job[${getJobName()}] already stopped.")
            return
        }
        logger.info("Job[${getJobName()}]: Start collection $collectionName.")
        val pageSize = batchSize
        var querySize: Int
        var lastId = ObjectId(MIN_OBJECT_ID)
        var sum = 0L
        measureNanoTime {
            do {
                val query = buildQuery()
                    .addCriteria(Criteria.where(ID).gt(lastId))
                    .limit(batchSize)
                    .with(Sort.by(ID).ascending())
                val fields = query.fields()
                entityClass().declaredMemberProperties.forEach {
                    fields.include(it.name)
                }
                val data = mongoTemplate.find<Map<String, Any?>>(
                    query,
                    collectionName,
                )
                if (data.isEmpty()) {
                    break
                }
                if (concurrentLevel >= JobConcurrentLevel.ROW) {
                    runAsync(data) { runRow(it, collectionName, context) }
                } else {
                    data.forEach { runRow(it, collectionName, context) }
                }
                sum += data.size
                querySize = data.size
                lastId = data.last()[ID] as ObjectId
                report(context)
            } while (querySize == pageSize && shouldRun())
        }.apply {
            val elapsedTime = HumanReadable.time(this)
            onRunCollectionFinished(collectionName, context)
            undoList.remove(collectionName)
            logger.info("Job[${getJobName()}]: collection $collectionName run completed,sum [$sum] elapse $elapsedTime")
        }
    }

    /**
     * 异步执行task列表
     * 通过信号量控制最大任务数,达到最大任务数，线程会阻塞等待
     * @param tasks 任务列表
     * @param block 处理函数
     * */
    private fun <T> runAsync(
        tasks: Iterable<T>,
        produce: Boolean = false,
        block: (it: T) -> Unit,
    ) {
        hasAsyncTask = true
        tasks.forEach {
            val task = IdentityTask(taskId) { block(it) }
            executor.executeWithId(task, produce, permitsPerSecond)
        }
    }

    /**
     * 在上下文中执行单个任务
     * @param data 待处理的数据
     * @param collectionName 数据所在表名
     * @param context 任务上下文
     * */
    private fun runRow(
        data: Map<String, Any?>,
        collectionName: String,
        context: Context,
    ) {
        try {
            val resultMap = data.toMutableMap()
            resultMap[JAVA_ID] = resultMap[ID].toString()
            run(mapToEntity(resultMap), collectionName, context)
            context.success.incrementAndGet()
        } catch (e: Exception) {
            context.failed.incrementAndGet()
            logger.error(e.message, e)
        } finally {
            context.total.incrementAndGet()
        }
    }

    override fun capture(): TJobFailover {
        return with(recoverableJobContext) {
            TJobFailover(
                name = getJobName(),
                createdBy = hostName(),
                createdDate = LocalDateTime.now(),
                success = success.get(),
                failed = failed.get(),
                total = total.get(),
                data = undoList.toJsonString(),
            )
        }
    }

    override fun reply(snapshot: TJobFailover) {
        with(snapshot) {
            recoverableJobContext.reset()
            recoverableJobContext.success.addAndGet(success)
            recoverableJobContext.failed.addAndGet(failed)
            recoverableJobContext.total.addAndGet(total)
            data?.let { data -> recoverableJobContext.undoCollectionNames.addAll(data.readJsonString()) }
        }
        recover = true
    }

    private fun hostName(): String {
        return InetAddress.getLocalHost().hostName
    }

    companion object {
        private val logger = LoggerHolder.jobLogger

        // 用于dao层转换
        private const val JAVA_ID = "id"

        // 30s单条记录执行超时时间
        private const val WAIT_TIMEOUT = 30_000L
    }
}
