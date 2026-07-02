package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.common.metadata.routing.NodeShardReadSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service

@Service
class NodeCollectionModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    @Autowired(required = false) private val nodeShardReadSupport: NodeShardReadSupport? = null,
) {
    fun statNodeNum(): MutableMap<String, Long> {
        val result = mutableMapOf<String, Long>()
        val collections = (0 until SHARDING_COUNT).map { "${COLLECTION_NAME}_$it" }
        if (nodeShardReadSupport != null) {
            nodeShardReadSupport.forEachShardGroup(collections) { template, collection, _ ->
                result[collection] = (result[collection] ?: 0L) +
                    template.getCollection(collection).estimatedDocumentCount()
            }
        } else {
            for (i in 0 until SHARDING_COUNT) {
                val collection = "${COLLECTION_NAME}_$i"
                result[collection] = mongoTemplate.getCollection(collection).estimatedDocumentCount()
            }
        }
        return result
    }

    companion object {
        private const val COLLECTION_NAME = "node"
        private const val SHARDING_COUNT = 256
    }
}
