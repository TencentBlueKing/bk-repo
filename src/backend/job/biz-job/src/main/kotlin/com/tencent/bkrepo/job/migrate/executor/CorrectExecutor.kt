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

package com.tencent.bkrepo.job.migrate.executor

import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.executor.handler.MigrateFailedHandler
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateRepoStorageUtils.buildThreadPoolExecutor
import com.tencent.bkrepo.job.migrate.utils.NodeIterator
import com.tencent.bkrepo.job.migrate.utils.TransferDataExecutor
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Component
class CorrectExecutor(
    properties: MigrateRepoStorageProperties,
    fileReferenceClient: FileReferenceClient,
    migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    migrateFailedNodeDao: MigrateFailedNodeDao,
    storageService: StorageService,
    executingTaskRecorder: ExecutingTaskRecorder,
    private val migrateFailedHandler: MigrateFailedHandler,
    private val transferDataExecutor: TransferDataExecutor,
    private val mongoTemplate: MongoTemplate,
) : BaseTaskExecutor(
    properties,
    migrateRepoStorageTaskDao,
    migrateFailedNodeDao,
    fileReferenceClient,
    storageService,
    executingTaskRecorder,
) {
    /**
     * 用于执行数据矫正的线程池
     */
    private val correctExecutor: ThreadPoolExecutor by lazy {
        buildThreadPoolExecutor("correct-repo-storage-%d")
    }

    override fun executor() = correctExecutor

    override fun close(timeout: Long, unit: TimeUnit) {
        correctExecutor.shutdown()
        correctExecutor.awaitTermination(timeout, unit)
    }

    override fun doExecute(context: MigrationContext) {
        with(context) {
            val iterator = NodeIterator(task, mongoTemplate)
            iterator.forEach { node ->
                context.incTransferringCount()
                transferDataExecutor.execute {
                    try {
                        correctNode(context, node)
                    } catch (e: Exception) {
                        migrateFailedHandler.handle(task, node, e)
                    } finally {
                        context.decTransferringCount()
                    }
                }
            }
            context.waitAllTransferFinished()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CorrectExecutor::class.java)
    }
}
