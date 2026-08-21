package com.tencent.bkrepo.common.mongo.routing

import com.mongodb.client.MongoClient
import com.tencent.bkrepo.common.mongo.MongoAutoConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.test.context.ContextConfiguration

@Document(PROBE_COLLECTION)
@CompoundIndexes(CompoundIndex(name = "name_idx", def = "{'name': 1}", background = true))
private data class TIndexProbe(val name: String)

private const val PROBE_COLLECTION = "routing_index_probe"
private const val DEFAULT_DB = "routing_default_db"
private const val INSTANCE_DB = "routing_instance_db"

@DataMongoTest
@ContextConfiguration(classes = [MongoTestConfiguration::class])
class RoutingInstanceIndexCreationTest {

    @Autowired
    private lateinit var mongoClient: MongoClient

    @AfterEach
    fun cleanUp() {
        mongoClient.getDatabase(DEFAULT_DB).drop()
        mongoClient.getDatabase(INSTANCE_DB).drop()
    }

    /**
     * 复现：实例库模板共享 default 的 MappingContext(autoIndexCreation=true) 时，
     * MongoTemplate 构造器把 MappingContext 的事件发布器改指向自己，
     * 之后任何实体首次映射都会在实例库建索引连带建集合（即使 routing-state=OFF）。
     */
    @Test
    fun `shared converter leaks collections into routing instance database`() {
        val defaultConverter = converterFor(DEFAULT_DB, autoIndexCreation = true)
        MongoTemplate(factoryFor(DEFAULT_DB), defaultConverter)
        MongoTemplate(factoryFor(INSTANCE_DB), defaultConverter)

        defaultConverter.mappingContext.getPersistentEntity(TIndexProbe::class.java)

        assertTrue(collectionExists(INSTANCE_DB))
        assertFalse(collectionExists(DEFAULT_DB))
    }

    @Test
    fun `dedicated converter keeps routing instance database clean`() {
        val defaultConverter = converterFor(DEFAULT_DB, autoIndexCreation = true)
        MongoTemplate(factoryFor(DEFAULT_DB), defaultConverter)
        val instanceTemplate = MongoTemplate(factoryFor(INSTANCE_DB), converterFor(INSTANCE_DB, false))

        defaultConverter.mappingContext.getPersistentEntity(TIndexProbe::class.java)

        assertFalse(collectionExists(INSTANCE_DB))
        assertTrue(collectionExists(DEFAULT_DB))

        // 双写期 ShardingMongoDao 仍需在实例库显式建索引，不能被一并禁掉
        instanceTemplate.indexOps(PROBE_COLLECTION).ensureIndex(Index().on("name", Sort.Direction.ASC))
        assertTrue(collectionExists(INSTANCE_DB))
    }

    private fun factoryFor(database: String) = SimpleMongoClientDatabaseFactory(mongoClient, database)

    private fun converterFor(database: String, autoIndexCreation: Boolean): MappingMongoConverter =
        MongoAutoConfiguration.createConverter(factoryFor(database), autoIndexCreation)

    private fun collectionExists(database: String): Boolean =
        mongoClient.getDatabase(database).listCollectionNames().any { it == PROBE_COLLECTION }
}
