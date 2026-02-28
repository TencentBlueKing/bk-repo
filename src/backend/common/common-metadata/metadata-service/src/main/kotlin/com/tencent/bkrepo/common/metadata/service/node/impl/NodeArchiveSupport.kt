package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.pojo.separation.NodeFilterInfo
import com.tencent.bkrepo.common.metadata.pojo.separation.SeparationContent
import com.tencent.bkrepo.common.metadata.pojo.separation.task.SeparationTaskRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeArchiveOperation
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.RESTORE
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.RESTORE_ARCHIVED
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.metadata.util.SeparationUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRestoreRequest
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NodeArchiveSupport(
    private val nodeBaseService: NodeBaseService,
    private val archiveClient: ArchiveClient,
    private val dataSeparationConfig: DataSeparationConfig? = null,
    private val separationNodeDao: SeparationNodeDao? = null,
    private val separationTaskService: SeparationTaskService? = null,
) : NodeArchiveOperation {
    val nodeDao: NodeDao = nodeBaseService.nodeDao
    val repositoryDao: RepositoryDao = nodeBaseService.repositoryDao

    override fun archiveNode(nodeArchiveRequest: NodeArchiveRequest) {
        with(nodeArchiveRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().set(TNode::archived.name, true)
            nodeDao.updateFirst(query, update)
            logger.info("Archive node $projectId/$repoName/$fullPath.")
        }
    }

    override fun restoreNode(nodeArchiveRequest: NodeArchiveRequest) {
        with(nodeArchiveRequest) {
            val query = NodeQueryHelper.nodeQuery(projectId, repoName, fullPath)
            val update = Update().set(TNode::archived.name, false)
                .set(TNode::lastAccessDate.name, LocalDateTime.now())
            nodeDao.updateFirst(query, update)
            logger.info("Restore node $projectId/$repoName/$fullPath.")
            if (isSeparationEnabled(projectId, repoName)) {
                restoreColdArchiveNode(projectId, repoName, fullPath)
            }
        }
    }

    override fun restoreNode(nodeRestoreRequest: NodeArchiveRestoreRequest): List<String> {
        with(nodeRestoreRequest) {
            val query = NodeQueryHelper.queryArchiveNode(projectId, repoName, path, metadata)
            query.limit(limit)
            val nodes = nodeDao.find(query)
            logger.info("Find ${nodes.size} nodes to restore.")
            val repo = repositoryDao.findByNameAndType(projectId, repoName) ?: throw RepoNotFoundException(repoName)
            val storageCredentialsKey = repo.credentialsKey
            val restoredPaths = nodes.map {
                val sha256 = it.sha256!!
                if (it.archived == true) {
                    val req = ArchiveFileRequest(sha256, storageCredentialsKey, operator)
                    archiveClient.restore(req)
                } else {
                    val req = UncompressFileRequest(sha256, storageCredentialsKey, operator)
                    archiveClient.uncompress(req)
                }
                logger.info("Restoring node $$projectId/$repoName/${it.fullPath}.")
                it.fullPath
            }.toMutableList()

            if (isSeparationEnabled(projectId, repoName)) {
                val coldPaths = restoreColdArchiveNodes(projectId, repoName, path, metadata)
                restoredPaths.addAll(coldPaths)
            }
            return restoredPaths
        }
    }

    override fun getArchivableSize(projectId: String, repoName: String?, days: Int, size: Long?): Long {
        val cutoffTime = LocalDateTime.now().minus(Duration.ofDays(days.toLong()))
        val criteria = where(TNode::folder).isEqualTo(false)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::sha256).ne(FAKE_SHA256)
            .and(TNode::archived).ne(true)
            .and(TNode::projectId).isEqualTo(projectId)
            .orOperator(
                where(TNode::lastAccessDate).isEqualTo(null),
                where(TNode::lastAccessDate).lt(cutoffTime),
            ).apply {
                repoName?.let { and(TNode::repoName).isEqualTo(it) }
                size?.let { and(TNode::size).gt(it) }
            }
        var total = nodeBaseService.aggregateComputeSize(criteria)
        if (isSeparationEnabled(projectId, repoName)) {
            total += aggregateColdArchivableSize(projectId, repoName, cutoffTime, size)
        }
        return total
    }

    private fun isSeparationEnabled(projectId: String, repoName: String?): Boolean {
        if (dataSeparationConfig == null || separationNodeDao == null || separationTaskService == null) return false
        if (dataSeparationConfig.specialSeparateRepos.isEmpty()) return false
        return if (repoName != null) {
            SeparationUtils.matchesConfigRepos("$projectId/$repoName", dataSeparationConfig.specialSeparateRepos)
        } else {
            dataSeparationConfig.specialSeparateRepos.any { configRepo ->
                val projectPattern = configRepo.substringBefore("/")
                Regex(projectPattern.replace("*", ".*")).matches(projectId)
            }
        }
    }

    private fun restoreColdArchiveNode(projectId: String, repoName: String, fullPath: String) {
        try {
            val query = Query(
                Criteria.where(TSeparationNode::projectId.name).isEqualTo(projectId)
                    .and(TSeparationNode::repoName.name).isEqualTo(repoName)
                    .and(TSeparationNode::fullPath.name).isEqualTo(fullPath)
                    .and(TSeparationNode::deleted.name).isEqualTo(null)
            )
            separationTaskService!!.findDistinctSeparationDate(projectId, repoName).forEach { date ->
                separationNodeDao!!.findByQuery(query, date).forEach { nodeMap ->
                    createColdRestoreTask(projectId, repoName, fullPath, nodeMap, date)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to restore cold archive node $projectId/$repoName/$fullPath", e)
        }
    }

    private fun restoreColdArchiveNodes(
        projectId: String,
        repoName: String,
        path: String?,
        metadata: Map<String, String>,
    ): List<String> {
        val restoredPaths = mutableListOf<String>()
        try {
            val query = NodeQueryHelper.queryArchiveNode(projectId, repoName, path, metadata)
            separationTaskService!!.findDistinctSeparationDate(projectId, repoName).forEach { date ->
                separationNodeDao!!.findByQuery(query, date).forEach { nodeMap ->
                    val fullPath = nodeMap[TSeparationNode::fullPath.name]?.toString() ?: return@forEach
                    createColdRestoreTask(projectId, repoName, fullPath, nodeMap, date)
                    restoredPaths.add(fullPath)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to restore cold archive nodes for $projectId/$repoName", e)
        }
        return restoredPaths
    }

    private fun createColdRestoreTask(
        projectId: String,
        repoName: String,
        fullPath: String,
        nodeMap: Map<String, Any?>,
        separationDate: LocalDateTime,
    ) {
        val taskType = if (nodeMap[TSeparationNode::archived.name]?.toString() == "true") RESTORE_ARCHIVED else RESTORE
        separationTaskService!!.createSeparationTask(
            SeparationTaskRequest(
                projectId = projectId,
                repoName = repoName,
                type = taskType,
                separateAt = separationDate.format(DateTimeFormatter.ISO_DATE_TIME),
                content = SeparationContent(paths = mutableListOf(NodeFilterInfo(path = fullPath)))
            )
        )
        logger.info("Created restore task for cold node $projectId/$repoName$fullPath.")
    }

    private fun aggregateColdArchivableSize(
        projectId: String,
        repoName: String?,
        cutoffTime: LocalDateTime,
        minSize: Long?,
    ): Long {
        return try {
            val separationDates = separationTaskService!!.findDistinctSeparationDate(projectId, repoName)
            val criteria = Criteria.where(TSeparationNode::folder.name).isEqualTo(false)
                .and(TSeparationNode::deleted.name).isEqualTo(null)
                .and(TSeparationNode::sha256.name).ne(FAKE_SHA256)
                .and(TSeparationNode::archived.name).ne(true)
                .and(TSeparationNode::projectId.name).isEqualTo(projectId)
                .orOperator(
                    Criteria.where(TSeparationNode::lastAccessDate.name).isEqualTo(null),
                    Criteria.where(TSeparationNode::lastAccessDate.name).lt(cutoffTime),
                ).apply {
                    repoName?.let { and(TSeparationNode::repoName.name).isEqualTo(it) }
                    minSize?.let { and(TSeparationNode::size.name).gt(it) }
                }
            separationDates.sumOf { date -> separationNodeDao!!.aggregateSizeByQuery(criteria, date) }
        } catch (e: Exception) {
            logger.error("Failed to aggregate cold archivable size for $projectId/$repoName", e)
            0L
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeArchiveSupport::class.java)
    }
}
