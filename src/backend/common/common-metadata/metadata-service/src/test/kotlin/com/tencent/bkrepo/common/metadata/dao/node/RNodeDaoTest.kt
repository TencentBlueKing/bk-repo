package com.tencent.bkrepo.common.metadata.dao.node

import com.mongodb.ConnectionString
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.mongo.reactive.dao.ShardingMongoReactiveDao
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.UpdateDefinition
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import reactor.core.publisher.Mono

class RNodeDaoTest {

    @Test
    fun `updateFirst touches lastModifiedDate`() = runBlocking {
        val mongoTemplate = RecordingReactiveMongoTemplate()
        val dao = RNodeDao()
        dao.reactiveMongoTemplate = mongoTemplate
        setShardingFields(dao)

        val query = Query(where(TNode::projectId).isEqualTo("project-id"))
        val update = Update().set(TNode::size.name, 1L)

        dao.updateFirst(query, update)

        val setDocument = mongoTemplate.lastUpdate!!.updateObject["\$set"] as Document
        assertEquals(1L, setDocument[TNode::size.name])
        assertTrue(setDocument.containsKey(TNode::lastModifiedDate.name))
    }

    private fun setShardingFields(dao: RNodeDao) {
        val projectIdField = TNode::class.java.getDeclaredField(TNode::projectId.name)
        setField(
            ShardingMongoReactiveDao::class.java,
            dao,
            "shardingFields",
            linkedMapOf(TNode::projectId.name to projectIdField),
        )
        setField(ShardingMongoReactiveDao::class.java, dao, "shardingCount", 1)
    }

    private fun setField(type: Class<*>, target: Any, name: String, value: Any) {
        val field = type.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private class RecordingReactiveMongoTemplate : ReactiveMongoTemplate(
        SimpleReactiveMongoDatabaseFactory(ConnectionString("mongodb://localhost:27017/test")),
    ) {
        var lastUpdate: UpdateDefinition? = null

        override fun updateFirst(
            query: Query,
            update: UpdateDefinition,
            collectionName: String,
        ): Mono<UpdateResult> {
            lastUpdate = update
            return Mono.just(UpdateResult.acknowledged(1, 1L, null))
        }
    }
}
