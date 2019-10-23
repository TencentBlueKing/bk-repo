package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataUpsertRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.updateFirst
import org.springframework.data.mongodb.core.upsert
import org.springframework.stereotype.Service

/**
 * 元数据服务
 *
 * @author: carrypan
 * @date: 2019-10-14
 */
@Service
class MetadataService @Autowired constructor(
    private val nodeService: NodeService,
    private val mongoTemplate: MongoTemplate
) {
    fun query(projectId: String, repoName: String, fullPath: String): Map<String, String> {
        logger.info("query, projectId: $projectId, repoName: $repoName, fullPath: $fullPath")
        nodeService.checkRepository(projectId, repoName)
        return mongoTemplate.findOne(createQuery(projectId, repoName, fullPath), TNode::class.java)?.metadata ?: emptyMap()
    }

    fun upsert(metadataUpsertRequest: MetadataUpsertRequest) {
        logger.info("upsert, metadataUpsertRequest: $metadataUpsertRequest")
        val projectId = metadataUpsertRequest.projectId
        val repoName = metadataUpsertRequest.repoName
        nodeService.checkRepository(projectId, repoName)

        val query = createQuery(projectId, repoName, metadataUpsertRequest.fullPath)
        val update = Update()
        metadataUpsertRequest.metadata.filterKeys { it.isNotBlank() }.forEach { (key, value) -> update.set("metadata.$key", value) }
        mongoTemplate.upsert(query, update, TNode::class)
    }

    fun delete(metadataDeleteRequest: MetadataDeleteRequest) {
        logger.info("delete, metadataDeleteRequest: $metadataDeleteRequest")
        val projectId = metadataDeleteRequest.projectId
        val repoName = metadataDeleteRequest.repoName
        nodeService.checkRepository(projectId, repoName)
        val query = createQuery(projectId, repoName, metadataDeleteRequest.fullPath)

        val update = Update()
        metadataDeleteRequest.keyList.filter { it.isNotBlank() }.forEach {
            update.unset("metadata.$it")
        }
        mongoTemplate.updateFirst(query, update, TNode::class)
    }

    private fun createQuery(projectId: String, repoName: String, fullPath: String): Query {
        val formattedPath = NodeUtils.formatFullPath(fullPath)
        return Query(Criteria.where("projectId").`is`(projectId)
                        .and("repoName").`is`(repoName)
                        .and("fullPath").`is`(formattedPath)
                        .and("deleted").`is`(null)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MetadataService::class.java)
    }
}
