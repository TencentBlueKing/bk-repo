package com.tencent.bkrepo.opdata.service.model

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service

@Service
class NodeCollectionModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate
) {
    companion object {
        private const val COLLECTION_NAME = "node"
        private const val SHARDING_COUNT = 256
    }

    fun statNodeNum(): MutableMap<String, Long> {
        val result = mutableMapOf<String, Long>()
        for (i in 0..SHARDING_COUNT) {
            val collection = "${COLLECTION_NAME}_$i"
            result[collection] = mongoTemplate.getCollection(collection).estimatedDocumentCount()
        }
        return result
    }
}