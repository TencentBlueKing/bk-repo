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

import com.google.common.base.CaseFormat.LOWER_CAMEL
import com.google.common.base.CaseFormat.UPPER_CAMEL
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.common.service.actuator.ActuatorConfiguration.Companion.SERVICE_INSTANCE_ID
import com.tencent.bkrepo.common.storage.config.StorageProperties
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
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
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
import org.springframework.data.domain.PageRequest
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
    private val repositoryService: RepositoryService,
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
            val repo = repositoryService.getRepoDetail(projectId, repoName)!!
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
     * 分页查询迁移任务
     *
     * @param state 任务状态，未指定时查询所有任务
     * @param pageRequest 分页请求
     *
     * @return 迁移任务分页
     */
    fun findTask(state: String?, pageRequest: PageRequest): Page<MigrateRepoStorageTask> {
        val count = migrateRepoStorageTaskDao.count(state)
        val records = migrateRepoStorageTaskDao.find(state, pageRequest).map { it.toDto() }
        return Pages.ofResponse(pageRequest, count, records)
    }

    /**
     * 尝试从队列中取出一个任务执行
     *
     * @return 未执行任务时返回null，否则返回触发执行的任务
     */
    fun tryExecuteTask(): MigrateRepoStorageTask? {
        MigrateRepoStorageTaskState.EXECUTABLE_STATE.forEach { state ->
            migrateRepoStorageTaskDao
                .executableTask(state)
                ?.let { executeTask(it.toDto()) }
                ?.let { return it }
        }

        return migrateRepoStorageTaskDao
            .correctableTask(migrateRepoStorageProperties.correctInterval)
            ?.toDto()
            ?.let { executeTask(it) }
    }

    /**
     * 指定仓库是否正在迁移中，startDate不为null时表示正在迁移中
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     *
     * @return 是否正在迁移
     */
    fun migrating(projectId: String, repoName: String): Boolean {
        return migrateRepoStorageTaskDao.migrating(projectId, repoName)
    }

    fun findTask(projectId: String, repoName: String): MigrateRepoStorageTask? {
        return migrateRepoStorageTaskDao.find(projectId, repoName)?.toDto()
    }

    /**
     * 回滚因进程重启或其他原因导致中断的任务状态
     */
    fun rollbackInterruptedTaskState() {
        migrateRepoStorageTaskDao.timeoutTasks(instanceId, migrateRepoStorageProperties.timeout).forEach {
            if (!executingTaskRecorder.executing(it.id!!)) {
                val rollbackState = when (it.state) {
                    MIGRATING.name -> PENDING.name
                    CORRECTING.name -> MIGRATE_FINISHED.name
                    MIGRATING_FAILED_NODE.name -> CORRECT_FINISHED.name
                    FINISHING.name -> MIGRATE_FAILED_NODE_FINISHED.name
                    else -> throw IllegalStateException("cant rollback state[${it.state}]")
                }
                // 任务之前在本实例内执行，但可能由于进程重启或其他原因而中断，需要重置状态
                migrateRepoStorageTaskDao.updateState(it.id, it.state, rollbackState, it.lastModifiedDate)
                logger.info("rollback task[${it.projectId}/${it.repoName}] state[${it.state}] to [$rollbackState]")
            }
        }
    }

    private fun executeTask(task: MigrateRepoStorageTask): MigrateRepoStorageTask? {
        val projectId = task.projectId
        val repoName = task.repoName
        val executorName = when (task.state) {
            PENDING.name -> MigrateExecutor::class.simpleName!!
            MIGRATE_FINISHED.name -> CorrectExecutor::class.simpleName!!
            CORRECT_FINISHED.name -> MigrateFailedNodeExecutor::class.simpleName!!
            MIGRATE_FAILED_NODE_FINISHED.name -> FinishExecutor::class.simpleName!!
            else -> throw IllegalStateException("unsupported state[${task.state}], task[$projectId/$repoName]")
        }
        return executors[UPPER_CAMEL.to(LOWER_CAMEL, executorName)]!!.execute(buildContext(task))?.task
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
