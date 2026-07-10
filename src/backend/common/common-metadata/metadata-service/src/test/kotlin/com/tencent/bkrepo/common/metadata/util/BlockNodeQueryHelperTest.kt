package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.metadata.UT_PROJECT_ID
import com.tencent.bkrepo.common.metadata.UT_REPO_NAME
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.utils.BlockNodeUtils.buildBlockNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@DisplayName("BlockNode查询构造测试")
@DataMongoTest
@TestPropertySource(
    locations = ["classpath:bootstrap-ut.properties"]
)
class BlockNodeQueryHelperTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
) {

    @BeforeEach
    fun beforeEach() {
        mongoTemplate.remove(Query(), COLLECTION_NAME)
    }

    @Test
    fun testListQueryForOverwrittenBlockNode() {
        val oldNodeCreatedDate = LocalDateTime.now().minusMinutes(30L).truncatedTo(ChronoUnit.MILLIS)
        val oldNodeDeletedDate = LocalDateTime.now().minusMinutes(20L).truncatedTo(ChronoUnit.MILLIS)
        val oldBlock = mongoTemplate.insert(
            buildBlockNode().copy(
                createdDate = oldNodeCreatedDate.plusMinutes(1L),
                sha256 = "old-block",
                deleted = oldNodeDeletedDate,
            ),
            COLLECTION_NAME
        )
        mongoTemplate.insert(
            buildBlockNode().copy(
                createdDate = oldNodeDeletedDate.plusMinutes(1L),
                sha256 = "new-block",
            ),
            COLLECTION_NAME
        )

        val query = BlockNodeQueryHelper.listQuery(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = oldBlock.nodeFullPath,
            createdDate = oldNodeCreatedDate.format(DateTimeFormatter.ISO_DATE_TIME),
            range = null,
            includeDeleted = true,
            createdBefore = oldNodeDeletedDate,
        )
        val blocks = mongoTemplate.find(query, TBlockNode::class.java, COLLECTION_NAME)

        assertEquals(listOf(oldBlock.id), blocks.map { it.id })
    }

    companion object {
        private const val COLLECTION_NAME = "block_node_query_helper_test"
    }
}
