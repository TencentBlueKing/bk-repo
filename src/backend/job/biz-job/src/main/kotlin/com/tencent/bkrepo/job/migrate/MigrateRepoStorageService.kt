/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate

import com.google.common.base.CaseFormat
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.service.actuator.ActuatorConfiguration.Companion.SERVICE_INSTANCE_ID
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.executor.CorrectExecutor
import com.tencent.bkrepo.job.migrate.executor.FinishExecutor
import com.tencent.bkrepo.job.migrate.executor.MigrateExecutor
import com.tencent.bkrepo.job.migrate.executor.MigrateFailedNodeExecutor
import com.tencent.bkrepo.job.migrate.executor.TaskExecutor
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.CreateMigrateRepoStorageTaskRequest
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask.Companion.toDto
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECTING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECT_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.FINISHING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FAILED_NODE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.PENDING
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 仓库存储迁移服务
 */
@Service
class MigrateRepoStorageService(
    private val migrateRepoStorageProperties: MigrateRepoStorageProperties,
    protected val storageProperties: StorageProperties,
    private val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    private val executors: Map<String, TaskExecutor>,
    private val executingTaskRecorder: ExecutingTaskRecorder,
) {
    @Value(SERVICE_INSTANCE_ID)
    protected lateinit var instanceId: String

    /**
     * 迁移仓库存储
     *
     * @param request 迁移请求
     */
    fun createTask(request: CreateMigrateRepoStorageTaskRequest): MigrateRepoStorageTask {
        with(request) {
            if (migrateRepoStorageTaskDao.exists(projectId, repoName)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, "$projectId/$repoName")
            }
            val now = LocalDateTime.now()
            val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
            if (repo.storageCredentials?.key == dstCredentialsKey) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "src key cant be same as dst key")
            }

            val task = migrateRepoStorageTaskDao.insert(
                TMigrateRepoStorageTask(
                    id = null,
                    createdBy = operator,
                    createdDate = now,
                    lastModifiedDate = now,
                    lastModifiedBy = operator,
                    projectId = projectId,
                    repoName = repoName,
                    srcStorageKey = repo.storageCredentials?.key,
                    dstStorageKey = dstCredentialsKey
                )
            )
            logger.info("create migrate task for $projectId/$repoName success")
            return task.toDto()
        }
    }

    /**
     * 尝试从队列中取出一个任务执行
     *
     * @return 未执行任务时返回null，否则返回触发执行的任务
     */
    fun tryExecuteTask(): MigrateRepoStorageTask? {
        val task = migrateRepoStorageTaskDao.executableTask()?.toDto()
            ?: migrateRepoStorageTaskDao.correctableTask(migrateRepoStorageProperties.correctInterval)?.toDto()
            ?: return null

        val projectId = task.projectId
        val repoName = task.repoName
        val executorName = when (task.state) {
            PENDING.name -> MigrateExecutor::class.simpleName!!
            MIGRATE_FINISHED.name -> CorrectExecutor::class.simpleName!!
            CORRECT_FINISHED.name -> MigrateFailedNodeExecutor::class.simpleName!!
            MIGRATE_FAILED_NODE_FINISHED.name -> FinishExecutor::class.simpleName!!
            else -> throw IllegalStateException("unsupported state[${task.state}], task[$projectId/$repoName]")
        }
        val executor = executors[CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, executorName)]!!

        return if (executor.execute(buildContext(task))) {
            task
        } else {
            null
        }
    }

    /**
     * 是否存在指定仓库的迁移任务
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     *
     * @return 是否正在迁移
     */
    fun migrating(projectId: String, repoName: String): Boolean {
        return migrateRepoStorageTaskDao.exists(projectId, repoName)
    }

    /**
     * 回滚因进程重启或其他原因导致中断的任务状态
     */
    fun rollbackInterruptedTaskState() {
        migrateRepoStorageTaskDao.timeoutTasks(instanceId, migrateRepoStorageProperties.timeout).forEach {
            if (executingTaskRecorder.executing(it.id!!)) {
                val rollbackState = when (it.state) {
                    MIGRATING.name -> PENDING.name
                    CORRECTING.name -> MIGRATE_FINISHED.name
                    MIGRATING_FAILED_NODE.name -> CORRECT_FINISHED.name
                    FINISHING.name -> MIGRATE_FAILED_NODE_FINISHED.name
                    else -> throw IllegalStateException("cant rollback state[${it.state}]")
                }
                // 任务之前在本实例内执行，但可能由于进程重启或其他原因而中断，需要重置状态
                migrateRepoStorageTaskDao.save(it.copy(state = rollbackState))
            }
        }
    }

    private fun buildContext(task: MigrateRepoStorageTask): MigrationContext {
        val srcCredentials = getStorageCredentials(task.srcStorageKey)
        val dstCredentials = getStorageCredentials(task.dstStorageKey)
        return MigrationContext(
            task = task,
            srcCredentials = srcCredentials,
            dstCredentials = dstCredentials,
        )
    }

    private fun getStorageCredentials(key: String?): StorageCredentials {
        return if (key == null) {
            storageProperties.defaultStorageCredentials()
        } else {
            RepositoryCommonUtils.getStorageCredentials(key)!!
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateRepoStorageService::class.java)
    }
}
