package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.listener.event.metadata.MetadataDeletedEvent
import com.tencent.bkrepo.repository.listener.event.metadata.MetadataSavedEvent
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.service.util.QueryHelper
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import org.slf4j.LoggerFactory
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
class MetadataService(
    private val repositoryService: RepositoryService,
    private val nodeDao: NodeDao
) : AbstractService() {

    fun query(projectId: String, repoName: String, fullPath: String): Map<String, String> {
        repositoryService.checkRepository(projectId, repoName)
        return convert(nodeDao.findOne(QueryHelper.nodeQuery(projectId, repoName, fullPath, withDetail = true))?.metadata)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun save(request: MetadataSaveRequest) {
        request.apply {
            val fullPath = formatFullPath(fullPath)
            if (!metadata.isNullOrEmpty()) {
                repositoryService.checkRepository(projectId, repoName)
                val metadataList = convert(metadata)
                metadataList.forEach { doSave(projectId, repoName, fullPath, it) }
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
            val fullPath = formatFullPath(request.fullPath)
            this.takeIf { keyList.isNotEmpty() }?.run {
                repositoryService.checkRepository(projectId, repoName)
                val query = QueryHelper.nodeQuery(projectId, repoName, fullPath)
                val update = Update().pull(
                    TNode::metadata.name,
                    Query.query(Criteria.where(TMetadata::key.name).`in`(keyList))
                )
                nodeDao.updateMulti(query, update)
            }.also {
                publishEvent(MetadataDeletedEvent(this))
            }.also {
                logger.info("Delete metadata [$this] success.")
            }
        }
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
            return metadataMap?.filter { it.key.isNotBlank() }?.map { TMetadata(it.key, it.value) }.orEmpty()
        }

        fun convert(metadataList: List<TMetadata>?): Map<String, String> {
            return metadataList?.map { it.key to it.value }?.toMap().orEmpty()
        }
    }
}
