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

package com.tencent.bkrepo.job.batch.task.stat

import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.EmptyFolderCleanupJobContext
import com.tencent.bkrepo.job.config.properties.ActiveProjectEmptyFolderCleanupJobProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.time.Duration


/**
 * 空目录清理job
 */
@Component
@EnableConfigurationProperties(ActiveProjectEmptyFolderCleanupJobProperties::class)
class ActiveProjectEmptyFolderCleanupJob(
    private val properties: ActiveProjectEmptyFolderCleanupJobProperties,
    executor: ThreadPoolTaskExecutor,
    private val activeProjectService: ActiveProjectService,
    private val mongoTemplate: MongoTemplate,
    private val emptyFolderCleanup: EmptyFolderCleanup,
) : StatBaseJob(mongoTemplate, properties, executor) {

    override fun doStart0(jobContext: JobContext) {
        logger.info("start to do empty folder cleanup job for active projects")
        require(jobContext is EmptyFolderCleanupJobContext)
        doStatStart(jobContext, jobContext.activeProjects.keys)
        logger.info("empty folder cleanup job for active projects finished")
    }

    override fun runRow(row: StatNode, context: JobContext) {
        require(context is EmptyFolderCleanupJobContext)
        try {
            val node = emptyFolderCleanup.buildNode(
                id = row.id,
                projectId = row.projectId,
                repoName = row.repoName,
                path = row.path,
                fullPath = row.fullPath,
                folder = row.folder,
                size = row.size
            )
            emptyFolderCleanup.collectEmptyFolder(node, context)
        } catch (e: Exception) {
            logger.error("run empty folder clean for Node $row failed, ${e.message}")
        }
    }

    override fun getLockAtMostFor(): Duration {
        return Duration.ofDays(1)
    }

    override fun createJobContext(): EmptyFolderCleanupJobContext {
        val temp = mutableMapOf<String, Boolean>()
        activeProjectService.getActiveProjects().forEach {
            temp[it] = true
        }
        return EmptyFolderCleanupJobContext(
            activeProjects = temp
        )
    }

    override fun onRunProjectFinished(collection: String, projectId: String, context: JobContext) {
        require(context is EmptyFolderCleanupJobContext)
        logger.info("will filter empty folder in project $projectId")
        emptyFolderCleanup.emptyFolderHandler(
            collection = collection,
            context = context,
            deletedEmptyFolder = properties.deletedEmptyFolder,
            projectId = projectId,
            deleteFolderRepos = properties.deleteFolderRepos
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ActiveProjectEmptyFolderCleanupJob::class.java)
    }
}
