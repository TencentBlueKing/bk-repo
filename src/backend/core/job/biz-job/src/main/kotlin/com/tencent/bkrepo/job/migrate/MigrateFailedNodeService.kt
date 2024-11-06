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
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeFixer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * 处理迁移失败node服务
 */
@Service
class MigrateFailedNodeService(
    private val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val migrateFailedNodeFixer: MigrateFailedNodeFixer,
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
     * 尝试自动修复所有失败node都已经重试并再次失败的项目
     */
    @Async
    fun autoFix() {
        var pageRequest = Pages.ofRequest(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE)
        var tasks: List<TMigrateRepoStorageTask>
        do {
            tasks = migrateRepoStorageTaskDao.find(MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE.name, pageRequest)
            tasks.forEach {
                val existsFailedNode = migrateFailedNodeDao.existsFailedNode(it.projectId, it.repoName)
                val existsRetryableNode = migrateFailedNodeDao.existsRetryableNode(it.projectId, it.repoName)
                // 存在无法继续重试的node时尝试修复
                if (existsFailedNode && !existsRetryableNode) {
                    autoFix(it.projectId, it.repoName)
                }
            }
            pageRequest = pageRequest.withPage(pageRequest.pageNumber + 1)
        } while (tasks.isNotEmpty())
    }

    /**
     * 尝试自动修复迁移失败的node
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     */
    @Async
    fun autoFix(projectId: String, repoName: String) {
        migrateFailedNodeFixer.fix(projectId, repoName)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateFailedNodeService::class.java)
    }
}
