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

package com.tencent.bkrepo.job.batch.task.archive

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.job.ARCHIVE_FILE_COLLECTION
import com.tencent.bkrepo.job.RESTORE_ARCHIVED
import com.tencent.bkrepo.job.SEPARATE_ARCHIVED
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.config.properties.ArchivedNodeRestoreJobProperties
import com.tencent.bkrepo.job.separation.dao.SeparationNodeDao
import com.tencent.bkrepo.job.separation.model.TSeparationNode
import com.tencent.bkrepo.job.separation.pojo.NodeFilterInfo
import com.tencent.bkrepo.job.separation.pojo.SeparationContent
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskRequest
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/**
 * 归档节点恢复任务
 * 对文件数据已经恢复好的节点进行恢复，具体步骤如下：
 * 1. 找到对应的已归档的节点
 * 2. 恢复节点
 * 3. 删除归档数据
 * */
@Component
class ArchivedNodeRestoreJob(
    private val properties: ArchivedNodeRestoreJobProperties,
    private val archiveClient: ArchiveClient,
    private val nodeService: NodeService,
    private val separationTaskService: SeparationTaskService,
    private val separationNodeDao: SeparationNodeDao,
) : MongoDbBatchJob<ArchivedNodeRestoreJob.ArchiveFile, NodeContext>(properties) {

    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf(ARCHIVE_FILE_COLLECTION)
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where("status").isEqualTo(ArchiveStatus.RESTORED))
    }

    override fun run(row: ArchiveFile, collectionName: String, context: NodeContext) {
        with(row) {
            // 查找热表中所有的sha256 key对应的node
            val hotNodes = listNode(sha256, storageCredentialsKey)
            
            // 查找冷表中所有的sha256对应的node
            val coldNodes = listSeparationNodesBySha256(sha256)
            
            // 恢复热表节点
            hotNodes.forEach {
                val request = NodeArchiveRequest(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    operator = lastModifiedBy,
                )
                nodeService.restoreNode(request)
                context.count.incrementAndGet()
                context.size.addAndGet(it.size)
                logger.info("Success to restore hot node ${it.projectId}/${it.repoName}${it.fullPath}.")
            }
            
            // 处理冷表节点：为每个冷表节点创建恢复任务
            coldNodes.forEach { separationNode ->
                val projectId = separationNode.projectId
                val repoName = separationNode.repoName
                val fullPath = separationNode.fullPath
                val separationDate = separationNode.separationDate!!
                
                logger.info("Found separation node for $projectId/$repoName$fullPath, creating restore task")
                val task = SeparationTaskRequest(
                    projectId = projectId,
                    repoName = repoName,
                    type = RESTORE_ARCHIVED,
                    separateAt = separationDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    content = SeparationContent(
                        paths = mutableListOf(
                            NodeFilterInfo(path = fullPath)
                        )
                    )
                )
                separationTaskService.createSeparationTask(task)
                logger.info("Created restore task for separation node: $projectId/$repoName$fullPath")
            }
            
            // 删除归档文件的条件：
            // 1. 没有冷表节点时才删除（无论是否有热表节点）
            // 2. 有冷表节点时不删除，等待下次Job运行时冷表节点恢复完成后再删除
            if (coldNodes.isEmpty()) {
                val request = ArchiveFileRequest(sha256, storageCredentialsKey, lastModifiedBy)
                archiveClient.delete(request)
                logger.info("Success to delete archive file $sha256. Hot nodes: ${hotNodes.size}, Cold nodes: 0")
            } else {
                logger.info(
                    "Skip deleting archive file $sha256, waiting for separation nodes recovery. " +
                        "Hot nodes: ${hotNodes.size}, Cold nodes: ${coldNodes.size}"
                )
            }
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): ArchiveFile {
        return ArchiveFile(
            id = row[ArchiveFile::id.name].toString(),
            storageCredentialsKey = row[ArchiveFile::storageCredentialsKey.name]?.toString(),
            lastModifiedBy = row[ArchiveFile::storageCredentialsKey.name].toString(),
            sha256 = row[ArchiveFile::sha256.name].toString(),
            size = row[ArchiveFile::size.name].toString().toLong(),
        )
    }

    override fun entityClass(): KClass<ArchiveFile> {
        return ArchiveFile::class
    }

    /**
     * 根据sha256查询冷表中的所有节点
     * 由于SeparationNodeDao是分表的，需要先查询配置了归档降冷任务的项目，然后根据分离日期去对应的分表查询
     */
    private fun listSeparationNodesBySha256(
        sha256: String
    ): List<TSeparationNode> {
        try {
            val result = mutableListOf<TSeparationNode>()
            // 查询所有配置了归档降冷任务的项目
            val projectList = separationTaskService.findProjectList(taskType = SEPARATE_ARCHIVED)
            
            projectList.forEach { projectId ->
                // 查询该项目下所有的分离日期
                val separationDates = separationTaskService.findDistinctSeparationDate(
                    projectId = projectId,
                    taskType = SEPARATE_ARCHIVED
                )
                
                // 根据每个分离日期去对应的分表查询
                separationDates.forEach { separationDate ->
                    val criteria = Criteria.where("sha256").isEqualTo(sha256)
                        .and("folder").isEqualTo(false)
                        .and("archived").isEqualTo(true)
                        .and("projectId").isEqualTo(projectId)
                        .and("separationDate").isEqualTo(separationDate)
                    val query = Query.query(criteria)
                    val nodes = separationNodeDao.find(query)
                    result.addAll(nodes)
                }
            }
            
            return result
        } catch (e: Exception) {
            logger.error("Failed to list separation nodes by sha256: $sha256", e)
            return emptyList()
        }
    }
    
    private fun listNode(sha256: String, storageCredentialsKey: String?): List<NodeCommonUtils.Node> {
        val query = Query.query(
            Criteria.where("sha256").isEqualTo(sha256)
                .and("archived").isEqualTo(true)
                .and("deleted").isEqualTo(null),
        )
        return NodeCommonUtils.findNodes(query, storageCredentialsKey, false)
    }

    data class ArchiveFile(
        var id: String? = null,
        val sha256: String,
        val size: Long,
        val storageCredentialsKey: String?,
        val lastModifiedBy: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ArchivedNodeRestoreJob::class.java)
    }
}
