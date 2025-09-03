package com.tencent.bkrepo.common.metadata.dao

import com.tencent.bkrepo.common.metadata.UT_CRC64_ECMA
import com.tencent.bkrepo.common.metadata.UT_PROJECT_ID
import com.tencent.bkrepo.common.metadata.UT_REPO_NAME
import com.tencent.bkrepo.common.metadata.UT_SHA256
import com.tencent.bkrepo.common.metadata.UT_USER
import com.tencent.bkrepo.common.metadata.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.dao.blocknode.BlockNodeDao
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
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
import java.time.LocalDateTime

@DisplayName("BlockNodeDao自定义分表键测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
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
        val shardingSequence = HashShardingUtils.shardingSequenceFor(UT_REPO_NAME, SHARDING_COUNT)
        val collectionName = "block_node_$shardingSequence"
        val block2 = mongoTemplate.findOne(Query(), TBlockNode::class.java, collectionName)
        assertEquals(block.id, block2.id)
    }

    private fun buildBlockNode(
        projectId: String = UT_PROJECT_ID,
        repoName: String = UT_REPO_NAME,
        fullPath: String = "/a/b/c.txt",
    ) = TBlockNode(
        id = null,
        createdBy = UT_USER,
        createdDate = LocalDateTime.now(),
        nodeFullPath = fullPath,
        startPos = 0,
        sha256 = UT_SHA256,
        crc64ecma = UT_CRC64_ECMA,
        projectId = projectId,
        repoName = repoName,
        size = 1024L,
    )
}
