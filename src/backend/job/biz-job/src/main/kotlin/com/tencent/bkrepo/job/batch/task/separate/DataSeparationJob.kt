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

package com.tencent.bkrepo.job.batch.task.separate

import com.google.common.base.CaseFormat
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.DataSeparationJobProperties
import com.tencent.bkrepo.job.separation.constant.RESTORE
import com.tencent.bkrepo.job.separation.constant.SEPARATE
import com.tencent.bkrepo.job.separation.executor.ColdDataRestoreTaskExecutor
import com.tencent.bkrepo.job.separation.executor.ColdDataSeparateTaskExecutor
import com.tencent.bkrepo.job.separation.executor.SeparationTaskExecutor
import com.tencent.bkrepo.job.separation.model.TSeparationTask
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskState
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 根据配置进行数据降冷
 */
@Component
@EnableConfigurationProperties(DataSeparationJobProperties::class)
class DataSeparationJob(
    val properties: DataSeparationJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val executors: Map<String, SeparationTaskExecutor>,
) : DefaultContextJob(properties) {

    override fun doStart0(jobContext: JobContext) {
        logger.info("start to run data separation job")
        val criteria = Criteria.where(TSeparationTask::state.name).`in`(STATE_LIST)
        val query = Query(criteria)
        val tasks = mongoTemplate.find(query, TSeparationTask::class.java, SEPARATION_TASK_COLLECTION_NAME)
        tasks.forEach {
            executeSeparationTask(it)
        }
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)


    private fun executeSeparationTask(task: TSeparationTask) {
        val executorName = when (task.type) {
            RESTORE -> ColdDataRestoreTaskExecutor::class.simpleName!!
            SEPARATE -> ColdDataSeparateTaskExecutor::class.simpleName!!
            else -> throw IllegalStateException("unsupported type[${task.type}], task ${task.id}")
        }
        return executors[CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, executorName)]!!
            .execute(buildSeparationContext(task))
    }

    private fun buildSeparationContext(task: TSeparationTask): SeparationContext {
        val repo = RepositoryCommonUtils.getRepositoryDetail(task.projectId, task.repoName)
        return SeparationContext(
            task = task,
            repo = repo
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DataSeparationJob::class.java)
        private const val SEPARATION_TASK_COLLECTION_NAME = "separation_task"
        private val STATE_LIST = listOf(SeparationTaskState.PENDING.name, SeparationTaskState.TERMINATED.name)
    }
}
