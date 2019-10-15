package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataUpsertRequest
import com.tencent.bkrepo.repository.util.NodeUtils
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
    private val mongoTemplate: MongoTemplate
) {
    fun query(repositoryId: String, fullPath: String): Map<String, String> {
        val formattedPath = NodeUtils.formatFullPath(fullPath)
        return mongoTemplate.findOne(Query(
                Criteria.where("repositoryId").`is`(repositoryId)
                        .and("fullPath").`is`(formattedPath)
                        .and("deleted").`is`(null)
        ), TNode::class.java)?.metadata ?: emptyMap()
    }

    fun upsert(metadataUpsertRequest: MetadataUpsertRequest) {
        val formattedPath = NodeUtils.formatFullPath(metadataUpsertRequest.fullPath)
        val query = Query(Criteria.where("repositoryId").`is`(metadataUpsertRequest.repositoryId)
                        .and("fullPath").`is`(formattedPath)
                        .and("deleted").`is`(null)
        )
        val update = Update()
        metadataUpsertRequest.metadata.filterKeys { it.isNotBlank() }.forEach { (key, value) -> update.set("metadata.$key", value) }
        mongoTemplate.upsert(query, update, TNode::class)
    }

    fun delete(metadataDeleteRequest: MetadataDeleteRequest) {
        val formattedPath = NodeUtils.formatFullPath(metadataDeleteRequest.fullPath)
        val query = Query(Criteria.where("repositoryId").`is`(metadataDeleteRequest.repositoryId)
                .and("fullPath").`is`(formattedPath)
                .and("deleted").`is`(null)
        )
        val update = Update()
        metadataDeleteRequest.keyList.filter { it.isNotBlank() }.forEach {
            update.unset("metadata.$it")
        }
        mongoTemplate.updateFirst(query, update, TNode::class)
    }
}
