/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.repository.job.base.CenterNodeJob
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 清理被标记为删除的node，同时减少文件引用
 */
@Component("JobServiceDeletedNodeCleanupJob")
class DeletedNodeCleanupJob(
    private val nodeDao: NodeDao,
    private val repositoryDao: RepositoryDao,
    private val fileReferenceService: FileReferenceService,
    private val clusterProperties: ClusterProperties
) : CenterNodeJob() {

    override fun start() {
        super.start()
    }

    override fun run() {
        val reserveDays = repositoryProperties.deletedNodeReserveDays
        if (reserveDays < 0) {
            logger.info("Reserve days[$reserveDays] for deleted nodes is less than 0, skip cleaning up.")
            return
        }
        val context = JobContext(LocalDateTime.now().minusDays(reserveDays))
        with(context) {
            repositoryDao.findAll().forEach { repo -> cleanupRepo(repo, context) }
            logger.info("[$total] nodes has been clean up, file[$file], folder[$folder]")
        }
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    private fun cleanupRepo(repo: TRepository, context: JobContext) {
        val criteria = where(TNode::projectId).isEqualTo(repo.projectId)
            .and(TNode::repoName).isEqualTo(repo.name)
            .and(TNode::deleted).lt(context.expireDate)
        val query = Query.query(criteria).with(PageRequest.of(0, PAGE_SIZE))
        var deletedNodeList = nodeDao.find(query)
        while (deletedNodeList.isNotEmpty()) {
            logger.info("Retrieved [${deletedNodeList.size}] deleted records from ${repo.projectId}/${repo.name}")
            deletedNodeList.forEach { node ->
                cleanUpNode(repo, node)
                if (node.folder) {
                    context.folder += 1
                } else {
                    context.file += 1
                }
            }
            context.total += deletedNodeList.size
            deletedNodeList = nodeDao.find(query)
        }
        // 仓库被标记为已删除，且该仓库下不存在任何节点时，删除仓库
        if (repo.deleted != null &&
            nodeDao.count(Query(
                where(TNode::projectId).isEqualTo(repo.projectId).and(TNode::repoName).isEqualTo(repo.name))
            ) == 0L
        ) {
            repositoryDao.deleteById(repo.id)
            logger.info("Clean up deleted repository[${repo.projectId}/${repo.name}] for no nodes remaining")
        }
    }

    private fun cleanUpNode(repo: TRepository, node: TNode) {
        var fileReferenceChanged = false
        try {
            val nodeQuery = Query.query(
                where(TNode::projectId).isEqualTo(node.projectId)
                    .and(TNode::repoName).isEqualTo(node.repoName)
                    .and(TNode::fullPath).isEqualTo(node.fullPath)
                    .and(TNode::deleted).isEqualTo(node.deleted)
            )
            nodeDao.remove(nodeQuery)
            if (!node.folder &&
                (node.clusterNames == null || node.clusterNames!!.contains(clusterProperties.self.name))
            ) {
                fileReferenceChanged = fileReferenceService.decrement(node.sha256!!, repo.credentialsKey)
            }
        } catch (ignored: Exception) {
            logger.error("Clean up deleted node[$node] failed.", ignored)
            if (fileReferenceChanged) {
                fileReferenceService.increment(node.sha256!!, repo.credentialsKey)
            }
        }
    }

    data class JobContext(
        val expireDate: LocalDateTime,
        var folder: Int = 0,
        var file: Int = 0,
        var total: Int = 0
    )

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val PAGE_SIZE = 1000
    }
}
