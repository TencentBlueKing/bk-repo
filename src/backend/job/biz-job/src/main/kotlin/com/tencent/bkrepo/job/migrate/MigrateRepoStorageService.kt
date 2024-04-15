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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.CreateMigrateRepoStorageTaskRequest
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask.Companion.toDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 仓库存储迁移服务
 */
@Service
class MigrateRepoStorageService(
    private val migrateRepoStorageProperties: MigrateRepoStorageProperties,
    private val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    private val executor: MigrateRepoStorageTaskExecutor,
) {
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
        return if (executor.execute(task)) {
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

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateRepoStorageService::class.java)
    }
}
