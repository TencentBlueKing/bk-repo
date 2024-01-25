/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.RunOnceTaskCleanupJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.replication.api.DistributionClient
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 清除已经执行完成的一次性分发任务
 */
@Component
@EnableConfigurationProperties(RunOnceTaskCleanupJobProperties::class)
class RunOnceTaskCleanupJob(
    private val properties: RunOnceTaskCleanupJobProperties,
    private val distributionClient: DistributionClient
) : DefaultContextMongoDbJob<RunOnceTaskCleanupJob.TaskData>(properties) {
    override fun start(): Boolean {
        return super.start()
    }

    override fun entityClass(): KClass<TaskData> {
        return TaskData::class
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        val fromDate = LocalDateTime.now().minusSeconds(properties.fixedDelay / 1000)
        return Query(
            Criteria.where(STATUS).isEqualTo(ReplicaStatus.COMPLETED)
                .and(REPLICA_TYPE).isEqualTo(ReplicaType.RUN_ONCE)
                .and(LAST_MODIFIED_DATE).lt(fromDate)
        )
    }

    override fun run(row: TaskData, collectionName: String, context: JobContext) {
        with(row) {
            try {
                distributionClient.deleteRunonceTask(name)
            } catch (e: Exception) {
                throw JobExecuteException(
                    "Failed to send task delete request for task $name|$key ${e.message}", e
                )
            }
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): TaskData {
        return TaskData(row)
    }

    data class TaskData(private val map: Map<String, Any?>) {
        val name: String by map
        val key: String by map
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val COLLECTION_NAME = "replica_task"
        private const val REPLICA_TYPE = "replicaType"
        private const val STATUS = "status"
    }
}
