package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.listener.event.metadata.MetadataDeletedEvent
import com.tencent.bkrepo.repository.listener.event.metadata.MetadataSavedEvent
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import com.tencent.bkrepo.repository.util.QueryHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 元数据服务
 *
 * @author: carrypan
 * @date: 2019-10-14
 */
@Service
class MetadataService : AbstractService() {

    @Autowired
    private lateinit var repositoryService: RepositoryService

    @Autowired
    private lateinit var nodeDao: NodeDao

    fun query(projectId: String, repoName: String, fullPath: String): Map<String, String> {
        repositoryService.checkRepository(projectId, repoName)
        return convert(nodeDao.findOne(QueryHelper.nodeQuery(projectId, repoName, fullPath))?.metadata)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun save(request: MetadataSaveRequest) {
        request.apply {
            if (!metadata.isNullOrEmpty()) {
                repositoryService.checkRepository(projectId, repoName)
                val fullPath = formatFullPath(fullPath)
                nodeDao.findOne(QueryHelper.nodeQuery(projectId, repoName, fullPath))?.let { node ->
                    val originalMetadata = convert(node.metadata).toMutableMap()
                    metadata!!.forEach { (key, value) -> originalMetadata[key] = value }
                    node.metadata = convert(originalMetadata)
                    nodeDao.save(node)
                } ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)
            } else {
                logger.info("Metadata key list is empty, skip saving[$this]")
                return
            }
        }.also {
            publishEvent(MetadataSavedEvent(it))
        }.also {
            logger.info("Save metadata [$it] success.")
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun delete(request: MetadataDeleteRequest) {
        request.apply {
            if (keyList.isNotEmpty()) {
                val fullPath = formatFullPath(request.fullPath)
                repositoryService.checkRepository(projectId, repoName)
                val query = QueryHelper.nodeQuery(projectId, repoName, fullPath)
                val update = Update().pull(
                    TNode::metadata.name,
                    Query.query(Criteria.where(TMetadata::key.name).`in`(keyList))
                )
                nodeDao.updateMulti(query, update)
            } else {
                logger.info("Metadata key list is empty, skip deleting[$this]")
                return
            }
        }.also {
            publishEvent(MetadataDeletedEvent(it))
        }.also {
            logger.info("Delete metadata [$it] success.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MetadataService::class.java)

        fun convert(metadataMap: Map<String, String>?): List<TMetadata> {
            return metadataMap?.filter { it.key.isNotBlank() }?.map { TMetadata(it.key, it.value) }.orEmpty()
        }

        fun convert(metadataList: List<TMetadata>?): Map<String, String> {
            return metadataList?.map { it.key to it.value }?.toMap().orEmpty()
        }

        fun convertOrNull(metadataList: List<TMetadata>?): Map<String, String>? {
            return metadataList?.map { it.key to it.value }?.toMap()
        }
    }
}
