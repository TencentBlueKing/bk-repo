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

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeAutoFixStrategy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * 处理迁移失败node服务
 */
@Service
class MigrateFailedNodeService(
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val migrateFailedNodeAutoFixStrategy: List<MigrateFailedNodeAutoFixStrategy>,
) {
    /**
     * 无法处理时，或已经手动处理成功则可以移除迁移失败的node
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 迁移失败的node完整路径
     */
    fun removeFailedNode(projectId: String, repoName: String, fullPath: String?) {
        val result = migrateFailedNodeDao.remove(projectId, repoName, fullPath)
        logger.info("remove [${result.deletedCount}] failed node of [$projectId/$repoName$fullPath]")
    }

    /**
     * 排查并修复异常后，重置迁移失败的node重试次数以便继续重试
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 迁移失败的node完整路径
     */
    fun resetRetryCount(projectId: String, repoName: String, fullPath: String?) {
        val result = migrateFailedNodeDao.resetRetryCount(projectId, repoName, fullPath)
        logger.info("reset [${result.modifiedCount}] retry count of [$projectId/$repoName$fullPath]")
    }

    /**
     * 尝试自动修复迁移失败的node
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     */
    @Async
    fun autoFix(projectId: String, repoName: String) {
        var pageRequest = Pages.ofRequest(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE)
        var failedNodes = migrateFailedNodeDao.page(projectId, repoName, pageRequest)
        while (failedNodes.isNotEmpty()) {
            failedNodes.forEach { autoFix(it) }
            pageRequest = pageRequest.withPage(pageRequest.pageNumber + 1)
            failedNodes = migrateFailedNodeDao.page(projectId, repoName, pageRequest)
        }
    }

    private fun autoFix(failedNode: TMigrateFailedNode) {
        val projectId = failedNode.projectId
        val repoName = failedNode.repoName
        for (strategy in migrateFailedNodeAutoFixStrategy) {
            if (strategy.fix(failedNode)) {
                logger.info("auto fix failed node[${failedNode.fullPath}] success, task[$projectId/$repoName]")
                resetRetryCount(projectId, repoName, failedNode.fullPath)
                return
            }
        }
        logger.info("auto fix failed node[${failedNode.fullPath}] failed, task[$projectId/$repoName]")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateFailedNodeService::class.java)
    }
}
