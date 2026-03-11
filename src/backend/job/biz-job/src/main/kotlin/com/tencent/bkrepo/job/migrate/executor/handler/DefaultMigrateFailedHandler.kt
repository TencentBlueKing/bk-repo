/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate.executor.handler

import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.Node
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.io.FileNotFoundException
import java.time.LocalDateTime

@Component
class DefaultMigrateFailedHandler(
    private val migrateFailedNodeDao: MigrateFailedNodeDao
) : MigrateFailedHandler {
    override fun handle(task: MigrateRepoStorageTask, node: Node, e: Exception) {
        saveMigrateFailedNode(task.id!!, node)
        val msg = "${task.state} node[${node.fullPath}] failed, task[${task.projectId}/${task.repoName}]"
        if (e is FileNotFoundException || e is IllegalStateException || e is StorageErrorException) {
            logger.warn(msg, e)
        } else {
            logger.error(msg, e)
        }
    }

    private fun saveMigrateFailedNode(taskId: String, node: Node) {
        with(node) {
            if (migrateFailedNodeDao.existsFailedNode(id)) {
                logger.info("failed node[${projectId}/${repoName}${fullPath}] already exists, nodeId[${id}]")
                return
            }

            val now = LocalDateTime.now()
            try {
                migrateFailedNodeDao.insert(
                    TMigrateFailedNode(
                        id = null,
                        createdDate = now,
                        lastModifiedDate = now,
                        nodeId = node.id,
                        taskId = taskId,
                        projectId = projectId,
                        repoName = repoName,
                        fullPath = fullPath,
                        sha256 = sha256,
                        md5 = md5,
                        size = size,
                        retryTimes = 0,
                    )
                )
                logger.info("insert failed node[${projectId}/${repoName}${fullPath}] success")
            } catch (ignore: DuplicateKeyException) {
                logger.warn("duplicate failed node[$node]", ignore)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultMigrateFailedHandler::class.java)
    }
}
