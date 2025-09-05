package com.tencent.bkrepo.common.metadata.dao

import com.tencent.bkrepo.common.api.mongo.ShardingDocument
import com.tencent.bkrepo.common.api.mongo.ShardingKeys
import com.tencent.bkrepo.common.metadata.UT_PROJECT_ID
import com.tencent.bkrepo.common.metadata.UT_REPO_NAME
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.dao.sharding.HashShardingMongoDao
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.springframework.test.context.TestPropertySource

@DisplayName("组合分表键测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ShardingKeysTest.TestShardingDao::class)
@TestPropertySource(
    locations = ["classpath:bootstrap-ut.properties"],
    properties = ["sharding.count=256"]
)
class ShardingKeysTest @Autowired constructor(
    private val shardingDao: TestShardingDao,
    private val mongoTemplate: MongoTemplate,
) {

    @Test
    fun testInsert() {
        shardingDao.insert(TTestSharding(null, UT_PROJECT_ID, UT_REPO_NAME))
        val sequence = HashShardingUtils.shardingSequenceFor(listOf(UT_PROJECT_ID, UT_REPO_NAME), SHARDING_COUNT)
        val data = mongoTemplate.findOne(Query(), TTestSharding::class.java, "${COLLECTION_NAME}_$sequence")
        assertNotNull(data)
    }

    @ShardingDocument(COLLECTION_NAME)
    @ShardingKeys(count = SHARDING_COUNT, columns = ["projectId", "repoName"])
    @CompoundIndexes(CompoundIndex(name = "projectId_repoName_idx", def = "{'projectId': 1, 'repoName': 1}"))
    data class TTestSharding(
        var id: String? = null,
        val projectId: String,
        val repoName: String,
    )

    @Repository
    class TestShardingDao : HashShardingMongoDao<TTestSharding>()

    companion object {
        private const val COLLECTION_NAME = "test_sharding"
    }
}
