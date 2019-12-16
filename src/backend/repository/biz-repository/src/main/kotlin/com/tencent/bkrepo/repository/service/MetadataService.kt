package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

/**
 * 元数据服务
 *
 * @author: carrypan
 * @date: 2019-10-14
 */
@Service
class MetadataService @Autowired constructor(
    private val repositoryService: RepositoryService,
    private val nodeDao: NodeDao
) {
    fun query(projectId: String, repoName: String, fullPath: String): Map<String, String> {
        repositoryService.checkRepository(projectId, repoName)
        return convert(nodeDao.findOne(QueryHelper.nodeQuery(projectId, repoName, fullPath, withDetail = true))?.metadata)
    }

    fun save(request: MetadataSaveRequest) {
        val projectId = request.projectId
        val repoName = request.repoName
        val fullPath = formatFullPath(request.fullPath)
        repositoryService.checkRepository(projectId, repoName)

        val metadataList = convert(request.metadata)
        metadataList.forEach { doSave(projectId, repoName, fullPath, it) }
        logger.info("Save metadata [$request] success.")
    }

    fun delete(request: MetadataDeleteRequest) {
        val projectId = request.projectId
        val repoName = request.repoName
        val fullPath = formatFullPath(request.fullPath)
        repositoryService.checkRepository(projectId, repoName)

        val query = QueryHelper.nodeQuery(projectId, repoName, fullPath)
        val update = Update().pull(TNode::metadata.name, Query.query(Criteria.where("key").`in`(request.keyList)))

        nodeDao.updateMulti(query, update)
        logger.info("Delete metadata [$request] success.")
    }

    private fun doSave(projectId: String, repoName: String, fullPath: String, metadata: TMetadata) {
        var query = QueryHelper.nodeMetadataQuery(projectId, repoName, fullPath, metadata.key)
        val update = if (nodeDao.exists(query)) {
            Update().set("metadata.$.value", metadata.value)
        } else {
            query = QueryHelper.nodeQuery(projectId, repoName, fullPath)
            Update().addToSet(TNode::metadata.name, metadata)
        }

        nodeDao.updateFirst(query, update)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MetadataService::class.java)

        fun convert(metadataMap: Map<String, String>?): List<TMetadata> {
            return metadataMap?.filter { it.key.isNotBlank() }?.map { TMetadata(it.key, it.value) } ?: emptyList()
        }

        fun convert(metadataList: List<TMetadata>?): Map<String, String> {
            return metadataList?.map { it.key to it.value }?.toMap() ?: emptyMap()
        }
    }
}
