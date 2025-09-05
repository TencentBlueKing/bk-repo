package com.tencent.bkrepo.common.metadata.dao

import com.tencent.bkrepo.common.metadata.UT_REPO_NAME
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.dao.blocknode.BlockNodeDao
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.utils.BlockNodeUtils.buildBlockNode
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource

@DisplayName("BlockNodeDao测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(BlockNodeDao::class, BlockNodeProperties::class)
@TestPropertySource(
    locations = ["classpath:bootstrap-ut.properties"],
    properties = ["sharding.count=256"]
)
class BlockNodeDaoTest @Autowired constructor(
    private val blockNodeDao: BlockNodeDao,
    private val mongoTemplate: MongoTemplate,
) {
    @Test
    fun test() {
        val block = blockNodeDao.insert(buildBlockNode())
        val collectionName = "block_node_${HashShardingUtils.shardingSequenceFor(UT_REPO_NAME, SHARDING_COUNT)}"
        val block2 = mongoTemplate.findOne(Query(), TBlockNode::class.java, collectionName)!!
        assertEquals(block.id, block2.id)
    }
}
