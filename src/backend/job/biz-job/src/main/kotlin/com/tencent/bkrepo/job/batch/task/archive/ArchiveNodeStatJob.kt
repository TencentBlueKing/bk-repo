/*
 *
 *  * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *  *
 *  * Copyright (C) 2025 Tencent.  All rights reserved.
 *  *
 *  * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *  *
 *  * A copy of the MIT License is included in this file.
 *  *
 *  *
 *  * Terms of the MIT License:
 *  * ---------------------------------------------------
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY
 */

package com.tencent.bkrepo.job.batch.task.archive

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.ArchiveNodeStatJobContext
import com.tencent.bkrepo.job.config.properties.ArchiveNodeStatJobProperties
import com.tencent.bkrepo.job.pojo.stat.StatNode
import com.tencent.bkrepo.oci.constant.DELETED
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass

/**
 * archive节点数据统计
 */
@Component
class ArchiveNodeStatJob(
    private val properties: ArchiveNodeStatJobProperties,
) : DefaultContextMongoDbJob<ArchiveNodeStatJob.ArchiveNode>(properties) {

    override fun collectionNames(): List<String> {
        return if (properties.archiveProjects.isEmpty()) {
            (0 until SHARDING_COUNT).map { "$COLLECTION_NAME_PREFIX$it" }.toList()
        } else {
            properties.archiveProjects.map {
                "node_${HashShardingUtils.shardingSequenceFor(it, SHARDING_COUNT)}"
            }
        }
    }

    override fun buildQuery(): Query {
        val criteria = Criteria.where("archived").isEqualTo(true)
            .and(DELETED).isEqualTo(null)
            .and(FOLDER).isEqualTo(false)
        return Query(criteria)
    }


    override fun mapToEntity(row: Map<String, Any?>): ArchiveNode = ArchiveNode(row)

    override fun entityClass(): KClass<ArchiveNode> = ArchiveNode::class

    /**
     * 最长加锁时间
     */
    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun run(row: ArchiveNode, collectionName: String, context: JobContext) {
        require(context is ArchiveNodeStatJobContext)
        val projectArchiveStatInfo = context.archiveStatInfo.getOrPut(row.projectId) {
            ArchiveNodeStatJobContext.ProjectArchiveInfo(row.projectId)
        }
        projectArchiveStatInfo.addRepoArchiveInfo(row)
    }

    override fun createJobContext(): ArchiveNodeStatJobContext {
        return ArchiveNodeStatJobContext()
    }


    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is ArchiveNodeStatJobContext)
        context.archiveStatInfo.forEach { (projectName, statInfo) ->
            val repoArchiveInfo = statInfo.repoArchiveInfo
            logger.info("update project $projectName archive stat info: ${repoArchiveInfo.toJsonString()}")
            if (repoArchiveInfo.isNotEmpty()) {
                val query = Query.query(Criteria.where(NAME).isEqualTo(projectName))
                mongoTemplate.find(query, Project::class.java).firstOrNull()?.let { project ->
                    val metadataMap = project.metadata.associateByTo(HashMap()) { it.key }
                    val newMetadata = ProjectMetadata(ARCHIVE_STAT_INFO, repoArchiveInfo.toJsonString())
                    metadataMap[newMetadata.key] = newMetadata
                    val update = Update().set(Project::metadata.name, metadataMap.values)
                    mongoTemplate.updateFirst(query, update, COLLECTION_PROJECT)
                }
            }
        }
    }

    data class Project(val name: String, val metadata: List<ProjectMetadata> = emptyList())

    data class ArchiveNode(
        val projectId: String,
        val repoName: String,
        val size: Long,
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[StatNode::projectId.name].toString(),
            map[StatNode::repoName.name].toString(),
            map[StatNode::size.name].toString().toLongOrNull() ?: 0,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveNodeStatJob::class.java)
        private const val COLLECTION_NAME_PREFIX = "node_"
        private const val COLLECTION_PROJECT = "project"
        const val ARCHIVE_STAT_INFO = "archiveStatInfo"

    }
}
