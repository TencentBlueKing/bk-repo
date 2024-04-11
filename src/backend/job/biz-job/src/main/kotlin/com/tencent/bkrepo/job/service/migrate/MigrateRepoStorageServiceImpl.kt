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

package com.tencent.bkrepo.job.service.migrate

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.pojo.CreateMigrateRepoStorageTaskRequest
import com.tencent.bkrepo.job.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.pojo.MigrateRepoStorageTask.Companion.toDto
import com.tencent.bkrepo.job.service.MigrateRepoStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MigrateRepoStorageServiceImp(
    private val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
) : MigrateRepoStorageService {
    override fun createTask(request: CreateMigrateRepoStorageTaskRequest): MigrateRepoStorageTask {
        with(request) {
            if (migrateRepoStorageTaskDao.exists(projectId, repoName)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, "$projectId/$repoName")
            }
            val now = LocalDateTime.now()
            val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
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

    override fun tryExecuteTask(): MigrateRepoStorageTask? {
        TODO()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateRepoStorageServiceImp::class.java)
    }
}
