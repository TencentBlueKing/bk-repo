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

package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ReplicationLockCleanupJobProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 清除replication服务生成的已使用完成的数据库锁
 */
@Component
@EnableConfigurationProperties(ReplicationLockCleanupJobProperties::class)
class ReplicationLockCleanupJob(
    properties: ReplicationLockCleanupJobProperties,
    private val mongoTemplate: MongoTemplate
) : DefaultContextJob(properties) {

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun doStart0(jobContext: JobContext) {
        mongoTemplate.find(
            Query(Criteria.where(LOCK_UNTIL).lt(LocalDateTime.now().minusDays(1))),
            ShedLockInfoData::class.java, COLLECTION_NAME
        ).forEach {
            removeLock(it)
        }
    }

    private fun removeLock(row: ShedLockInfoData) {
        with(row) {
            try {
                if (!id.startsWith(REPLICA_LOCK_NAME_PREFIX)) return
                val taskId = id.substringAfter(REPLICA_LOCK_NAME_PREFIX)
                val taskQuery = Query.query(Criteria.where(ID).isEqualTo(taskId))
                val data = mongoTemplate.find<Map<String, Any?>>(
                    taskQuery,
                    TASK_COLLECTION_NAME
                )
                if (data.isEmpty()) {
                    val nodeQuery = Query.query(Criteria.where(ID).isEqualTo(id))
                    mongoTemplate.remove(nodeQuery, COLLECTION_NAME)
                    return
                }
                val status: String = data.first()[TASK_STATUS] as String
                if (status == TASK_STATUS_FINISHED) {
                    val nodeQuery = Query.query(Criteria.where(ID).isEqualTo(id))
                    mongoTemplate.remove(nodeQuery, COLLECTION_NAME)
                }
            } catch (ignored: Exception) {
                logger.warn(
                    "Clean up replication lock shed_lock[$row] failed in collection[$COLLECTION_NAME].",
                    ignored
                )
            }
        }
    }

    data class ShedLockInfoData(
        val id: String,
        val lockUntil: String,
        val lockedAt: String,
    )

    companion object {
        private val logger = LoggerHolder.jobLogger
        const val COLLECTION_NAME = "shed_lock"
        const val TASK_COLLECTION_NAME = "replica_task"
        const val LOCKED_AT = "lockedAt"
        const val LOCK_UNTIL = "lockUntil"
        const val REPLICA_LOCK_NAME_PREFIX = "REPLICA_JOB_"
        const val TASK_STATUS = "status"
        const val TASK_STATUS_FINISHED = "COMPLETED"
    }
}
