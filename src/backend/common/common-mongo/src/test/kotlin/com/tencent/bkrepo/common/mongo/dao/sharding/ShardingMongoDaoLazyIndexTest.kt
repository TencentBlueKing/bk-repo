package com.tencent.bkrepo.common.mongo.dao.sharding

import com.mongodb.client.MongoClient
import com.mongodb.client.model.IndexOptions
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.api.util.sharding.ShardingUtils
import com.tencent.bkrepo.common.mongo.routing.MongoTestConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.test.context.ContextConfiguration

private const val PROBE_COLLECTION = "lazy_index_probe"
private const val PROBE_IDX = "projectId_fullPath_idx"
private const val PROBE_IDX_DEF = "{'projectId': 1, 'fullPath': 1, 'deleted': 1}"
private const val DEFAULT_DB = "lazy_index_default_db"
private const val SECONDARY_DB = "lazy_index_secondary_db"

@CompoundIndexes(CompoundIndex(name = PROBE_IDX, def = PROBE_IDX_DEF, unique = true, background = true))
private data class TShardProbe(val projectId: String, val fullPath: String, val deleted: Boolean = false)

private class ProbeDao(
    private val default: MongoTemplate,
    private val secondary: MongoTemplate,
) : ShardingMongoDao<TShardProbe>() {

    override fun determineShardingUtils(): ShardingUtils = HashShardingUtils

    override fun determineMongoTemplate(): MongoTemplate = default

    override fun determineCollectionName(entity: TShardProbe): String = PROBE_COLLECTION

    override fun writeTemplates(collectionName: String, context: Any?): List<MongoTemplate> =
        listOf(default, secondary)
}

@DataMongoTest
@ContextConfiguration(classes = [MongoTestConfiguration::class])
class ShardingMongoDaoLazyIndexTest {

    @Autowired
    private lateinit var mongoClient: MongoClient

    @AfterEach
    fun cleanUp() {
        mongoClient.getDatabase(DEFAULT_DB).drop()
        mongoClient.getDatabase(SECONDARY_DB).drop()
    }

    /**
     * hash 分表双写期：Default 的分表索引由 PostConstruct 负责，lazy 路径只在副本实例建索引。
     * 对 Default 重复 DDL 会在存量索引定义与代码不一致时抛 IndexOptionsConflict(85) 打断业务写入。
     */
    @Test
    fun `lazy ensure index skips default template for hash sharded collection`() {
        val defaultTemplate = templateFor(DEFAULT_DB)
        val secondaryTemplate = templateFor(SECONDARY_DB)
        // 模拟存量索引：同名但定义与代码不一致
        defaultTemplate.db.getCollection(PROBE_COLLECTION).createIndex(
            org.bson.Document("projectId", 1).append("fullPath", 1),
            IndexOptions().name(PROBE_IDX),
        )

        ProbeDao(defaultTemplate, secondaryTemplate).insert(TShardProbe(projectId = "p", fullPath = "/a.txt"))

        assertEquals(1, defaultTemplate.db.getCollection(PROBE_COLLECTION).countDocuments())
        // Default 上的存量索引保持原样，未被重复 DDL 触碰
        assertEquals(setOf("_id_", PROBE_IDX), indexNames(defaultTemplate))
        // 副本实例无存量索引，按当前定义建出来
        assertTrue(indexNames(secondaryTemplate).contains(PROBE_IDX))
    }

    private fun templateFor(database: String) =
        MongoTemplate(SimpleMongoClientDatabaseFactory(mongoClient, database))

    private fun indexNames(template: MongoTemplate) =
        template.indexOps(PROBE_COLLECTION).indexInfo.mapTo(mutableSetOf()) { it.name }
}
