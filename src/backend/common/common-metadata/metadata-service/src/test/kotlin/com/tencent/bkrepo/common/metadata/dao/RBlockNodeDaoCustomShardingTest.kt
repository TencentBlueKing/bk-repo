package com.tencent.bkrepo.common.metadata.dao

import com.tencent.bkrepo.common.metadata.UT_PROJECT_ID
import com.tencent.bkrepo.common.metadata.UT_REPO_NAME
import com.tencent.bkrepo.common.metadata.config.BlockNodeDaoConfiguration
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.dao.blocknode.RBlockNodeDao
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.utils.BlockNodeUtils.buildBlockNode
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource

@DisplayName("RBlockNodeDao自定义分表键测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(BlockNodeDaoConfiguration::class, BlockNodeProperties::class)
@TestPropertySource(
    locations = ["classpath:bootstrap-ut.properties"],
    properties = [
        "block-node.collection-name=block_node_v2",
        "block-node.sharding-columns=projectId,repoName",
        "block-node.sharding-count=$SHARDING_COUNT",
    ]
)
class RBlockNodeDaoCustomShardingTest @Autowired constructor(
    private val blockNodeDao: RBlockNodeDao,
    private val mongoTemplate: ReactiveMongoTemplate,
) {
    @Test
    fun test() {
        runBlocking {
            val block = blockNodeDao.insert(buildBlockNode())
            val shardingValues = listOf(UT_PROJECT_ID, UT_REPO_NAME)
            val collectionName =
                "block_node_v2_${HashShardingUtils.shardingSequenceFor(shardingValues, SHARDING_COUNT)}"
            val block2 = mongoTemplate.findOne(Query(), TBlockNode::class.java, collectionName).awaitSingle()
            assertEquals(block.id, block2.id)
        }
    }
}
