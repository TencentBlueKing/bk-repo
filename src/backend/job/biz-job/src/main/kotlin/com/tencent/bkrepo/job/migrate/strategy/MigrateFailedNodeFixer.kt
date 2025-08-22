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

package com.tencent.bkrepo.job.migrate.strategy

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.job.migrate.Constant.MAX_MIGRATE_FAILED_RETRY_TIMES
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MigrateFailedNodeFixer(
    private val migrateFailedNodeAutoFixStrategy: List<MigrateFailedNodeAutoFixStrategy>,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
) {
    fun fix(projectId: String, repoName: String) {
        var pageRequest = Pages.ofRequest(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE)
        var failedNodes = migrateFailedNodeDao.page(projectId, repoName, pageRequest)
        while (failedNodes.isNotEmpty()) {
            failedNodes.forEach { fix(it) }
            pageRequest = pageRequest.withPage(pageRequest.pageNumber + 1)
            failedNodes = migrateFailedNodeDao.page(projectId, repoName, pageRequest)
        }
    }

    fun fix(failedNode: TMigrateFailedNode): Boolean {
        if (failedNode.retryTimes < MAX_MIGRATE_FAILED_RETRY_TIMES) {
            // 不支持修复未完成重试的failedNode
            return false
        }

        val fullPath = failedNode.fullPath
        val projectId = failedNode.projectId
        val repoName = failedNode.repoName
        for (strategy in migrateFailedNodeAutoFixStrategy) {
            if (strategy.fix(failedNode)) {
                logger.info("auto fix failed node[${fullPath}] success, task[$projectId/$repoName]")
                // 修复成功后重置重试次数以便重新开始重试流程
                val result = migrateFailedNodeDao.resetRetryCount(failedNode.id!!)
                logger.info("reset [${result.modifiedCount}] retry count of [$projectId/$repoName$fullPath]")
                return true
            }
        }
        logger.info("auto fix failed node[${failedNode.fullPath}] failed, task[$projectId/$repoName]")
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateFailedNodeFixer::class.java)
    }
}
