package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.UT_CRC64_ECMA
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_USER
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DisplayName("迁移block node数据测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(MigrateBlockNodeCollectionServiceImpl::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class MigrateBlockNodeCollectionServiceImplTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val migrateService: MigrateBlockNodeCollectionServiceImpl
) {
    @Test
    fun testMigrate() {
        mockData()
        migrateService.migrate(
            OLD_COLLECTION_NAME_PREFIX,
            NEW_COLLECTION_NAME_PREFIX,
            newShardingColumns,
            SHARDING_COUNT
        )
    }

    private fun mockData(count: Int = 20): List<TBlockNode> {
        val mockData = ArrayList<TBlockNode>(count)
        for (i in 0 until count) {
            val repoName = "${UT_REPO_NAME}_$i"
            val sequence = HashShardingUtils.shardingSequenceFor(repoName, SHARDING_COUNT)
            val oldCollectionName = "${OLD_COLLECTION_NAME_PREFIX}_$sequence"
            val blockNode = buildBlockNode(repoName = repoName)
            mockData.add(blockNode)
            mongoTemplate.insert(blockNode, oldCollectionName)
        }
        return mockData
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

    companion object {
        private const val OLD_COLLECTION_NAME_PREFIX = "block_node"
        private const val NEW_COLLECTION_NAME_PREFIX = "block_node_v2"
        private val newShardingColumns = listOf(TBlockNode::projectId.name, TBlockNode::repoName.name)
    }
}
