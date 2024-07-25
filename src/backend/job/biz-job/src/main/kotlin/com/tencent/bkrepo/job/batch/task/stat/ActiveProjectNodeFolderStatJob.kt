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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.NodeFolderJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.config.properties.ActiveProjectNodeFolderStatJobProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 活跃项目下目录大小以及文件个数统计
 */
@Component
@EnableConfigurationProperties(ActiveProjectNodeFolderStatJobProperties::class)
class ActiveProjectNodeFolderStatJob(
    val properties: ActiveProjectNodeFolderStatJobProperties,
    executor: ThreadPoolTaskExecutor,
    private val activeProjectService: ActiveProjectService,
    private val mongoTemplate: MongoTemplate,
    private val nodeFolderStat: NodeFolderStat,
) : StatBaseJob(mongoTemplate, properties, executor) {

    override fun doStart0(jobContext: JobContext) {
        logger.info("start to do folder stat job for active projects")
        require(jobContext is NodeFolderJobContext)
        val extraCriteria = getExtraCriteria()
        doStatStart(jobContext, jobContext.activeProjects.keys, extraCriteria)
        logger.info("folder stat job for active projects finished")
    }

    fun getExtraCriteria(): Criteria {
        return Criteria().and(FOLDER).`is`(false)
    }

    override fun beforeRunProject(projectId: String) {
        if (properties.userMemory) return
        // 每次任务启动前要将redis上对应的key清理， 避免干扰
        val key = KEY_PREFIX + StringPool.COLON +
            FolderUtils.buildCacheKey(projectId = projectId, repoName = StringPool.EMPTY)
        nodeFolderStat.removeRedisKey(key)
    }

    override fun runRow(row: StatNode, context: JobContext) {
        require(context is NodeFolderJobContext)
        val node = nodeFolderStat.buildNode(
            id = row.id,
            projectId = row.projectId,
            repoName = row.repoName,
            path = row.path,
            fullPath = row.fullPath,
            folder = row.folder,
            size = row.size
        )
        nodeFolderStat.collectNode(
            node = node,
            context = context,
            useMemory = properties.userMemory,
            keyPrefix = KEY_PREFIX,
            cacheNumLimit = properties.cacheNumLimit
        )
    }

    override fun createJobContext(): NodeFolderJobContext {
        val temp = mutableMapOf<String, Boolean>()
        activeProjectService.getActiveProjects().forEach {
            temp[it] = true
        }
        return NodeFolderJobContext(
            activeProjects = temp
        )
    }

    /**
     * 将memory缓存中属于projectId下的记录写入DB中
     */
    override fun onRunProjectFinished(collection: String, projectId: String, context: JobContext) {
        require(context is NodeFolderJobContext)
        if (!properties.userMemory) {
            nodeFolderStat.updateRedisCache(
                context = context,
                force = true,
                keyPrefix = KEY_PREFIX,
                collectionName = null,
                projectId = projectId,
                cacheNumLimit = properties.cacheNumLimit
            )
        }

        logger.info("store cache to db with projectId $projectId")
        if (properties.userMemory) {
            nodeFolderStat.storeMemoryCacheToDB(context, collection, projectId)
        } else {
            nodeFolderStat.storeRedisCacheToDB(context, KEY_PREFIX, collection, projectId)
        }
    }

    /**
     * 最长加锁时间
     */
    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    companion object {
        private val logger = LoggerFactory.getLogger(ActiveProjectNodeFolderStatJob::class.java)
        private const val KEY_PREFIX = "activeProjectNode"
    }
}
