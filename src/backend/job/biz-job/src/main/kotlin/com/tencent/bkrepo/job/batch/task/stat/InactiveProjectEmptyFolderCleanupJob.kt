/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.ActiveProjectService
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.EmptyFolderCleanupJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.StatUtils.specialRepoRunCheck
import com.tencent.bkrepo.job.config.properties.InactiveProjectEmptyFolderCleanupJobProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass


/**
 * 非活跃项目下目录大小以及文件个数统计
 */
@Component
@EnableConfigurationProperties(InactiveProjectEmptyFolderCleanupJobProperties::class)
class InactiveProjectEmptyFolderCleanupJob(
    private val properties: InactiveProjectEmptyFolderCleanupJobProperties,
    private val activeProjectService: ActiveProjectService,
    private val emptyFolderCleanup: EmptyFolderCleanup,
) : DefaultContextMongoDbJob<InactiveProjectEmptyFolderCleanupJob.Node>(properties) {

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.toList()
    }

    override fun buildQuery(): Query {
        var criteria = Criteria.where(DELETED_DATE).`is`(null)
        if (
            !properties.runAllRepo && !specialRepoRunCheck(properties.specialDay)
            && properties.specialRepos.isNotEmpty()
        ) {
            criteria = criteria.and(REPO).nin(properties.specialRepos)
        }
        return Query(criteria)
    }

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(row)
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }

    fun statProjectCheck(
        projectId: String,
        context: EmptyFolderCleanupJobContext
    ): Boolean {
        return context.activeProjects[projectId] != null
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        require(context is EmptyFolderCleanupJobContext)
        if (statProjectCheck(row.projectId, context)) return
        val node = emptyFolderCleanup.buildNode(
            id = row.id,
            projectId = row.projectId,
            repoName = row.repoName,
            path = row.path,
            fullPath = row.fullPath,
            folder = row.folder,
            size = row.size
        )
        emptyFolderCleanup.collectEmptyFolderWithMemory(
            row = node, context = context, collectionName = collectionName,
            keyPrefix = KEY_PREFIX, useMemory = properties.userMemory,
            cacheNumLimit = properties.cacheNumLimit
        )
    }

    override fun getLockAtMostFor(): Duration {
        return Duration.ofDays(14)
    }

    override fun createJobContext(): EmptyFolderCleanupJobContext {
        beforeRunCollection()
        val temp = mutableMapOf<String, Boolean>()
        activeProjectService.getActiveProjects().forEach {
            temp[it] = true
        }
        return EmptyFolderCleanupJobContext(
            activeProjects = temp
        )
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        require(context is EmptyFolderCleanupJobContext)
        super.onRunCollectionFinished(collectionName, context)
        logger.info("will filter empty folder in $collectionName")

        if (!properties.userMemory) {
            emptyFolderCleanup.collectEmptyFolderWithRedis(
                context = context,
                force = true,
                keyPrefix = KEY_PREFIX,
                collectionName = collectionName,
                cacheNumLimit = properties.cacheNumLimit
            )
        }
        if (properties.userMemory) {
            emptyFolderCleanup.emptyFolderHandlerWithMemory(
                collection = collectionName,
                context = context,
                deletedEmptyFolder = properties.deletedEmptyFolder,
                runCollection = true,
                deleteFolderRepos = properties.deleteFolderRepos
            )
        } else {
            emptyFolderCleanup.emptyFolderHandlerWithRedis(
                collection = collectionName,
                keyPrefix = KEY_PREFIX,
                context = context,
                deletedEmptyFolder = properties.deletedEmptyFolder,
                runCollection = true,
                deleteFolderRepos = properties.deleteFolderRepos
            )
        }
    }

    private fun beforeRunCollection() {
        if (properties.userMemory) return
        // 每次任务启动前要将redis上对应的key清理， 避免干扰
        collectionNames().forEach {
            val key = KEY_PREFIX + StringPool.COLON + FolderUtils.buildCacheKey(
                collectionName = it, projectId = StringPool.EMPTY
            )
            emptyFolderCleanup.removeRedisKey(key)
        }
    }

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val folder: Boolean,
        val fullPath: String,
        val path: String,
        val size: Long
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Node::id.name].toString(),
            map[Node::projectId.name].toString(),
            map[Node::repoName.name].toString(),
            map[Node::folder.name] as Boolean,
            map[Node::fullPath.name].toString(),
            map[Node::path.name].toString(),
            map[Node::size.name].toString().toLongOrNull() ?: 0,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InactiveProjectEmptyFolderCleanupJob::class.java)
        const val FULL_PATH_IDX = "projectId_repoName_fullPath_idx"
        private const val COLLECTION_NAME_PREFIX = "node_"
        private const val KEY_PREFIX = "inactiveEmptyFolder"
    }
}