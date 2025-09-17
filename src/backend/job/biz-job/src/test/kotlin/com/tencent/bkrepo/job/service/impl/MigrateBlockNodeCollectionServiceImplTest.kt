package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.UT_CRC64_ECMA
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_USER
import org.bson.types.ObjectId
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
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

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
        var oldCount = 0L
        var newCount = 0L
        for (i in 0 until SHARDING_COUNT) {
            oldCount += mongoTemplate.count(Query(), "${OLD_COLLECTION_NAME_PREFIX}_$i")
            newCount += mongoTemplate.count(Query(), "${NEW_COLLECTION_NAME_PREFIX}_$i")
        }
        assertEquals(oldCount, newCount)
    }

    @Test
    fun testGetStartObjectIds() {
        val projectRepos = ArrayList<Pair<String, String>>(5)
        repeat(5) { projectRepos.add(Pair("${UT_PROJECT_ID}_$it", "${UT_REPO_NAME}_$it")) }
        projectRepos.add(Pair("${UT_PROJECT_ID}_6", "${UT_REPO_NAME}_0"))
        projectRepos.add(Pair("${UT_PROJECT_ID}_7", "${UT_REPO_NAME}_0"))

        // 插入数据到新表
        val expectedStartIds = HashMap<String, String>()
        projectRepos.forEach { prjRepo ->
            val newSequence =
                HashShardingUtils.shardingSequenceFor(listOf(prjRepo.first, prjRepo.second), SHARDING_COUNT)
            val oldSequence = HashShardingUtils.shardingSequenceFor(prjRepo.second, SHARDING_COUNT)
            val newCollection = "${NEW_COLLECTION_NAME_PREFIX}_$newSequence"
            val oldCollection = "${OLD_COLLECTION_NAME_PREFIX}_$oldSequence"
            repeat(11) {
                val block = mongoTemplate.insert(buildBlockNode(prjRepo.first, prjRepo.second), oldCollection)
                mongoTemplate.insert(block, newCollection)
                expectedStartIds[oldCollection] = block.id!!
            }
        }

        val func = MigrateBlockNodeCollectionServiceImpl::class.declaredFunctions.first {
            it.name == "getStartObjectIds"
        }
        func.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = func.call(
            migrateService,
            OLD_COLLECTION_NAME_PREFIX,
            NEW_COLLECTION_NAME_PREFIX,
            SHARDING_COUNT
        ) as Map<String, ObjectId>
        assertEquals(expectedStartIds.size, result.size)
        result.forEach { assertEquals(expectedStartIds[it.key], it.value.toString()) }
    }

    private fun mockData(count: Int = 20) {
        for (i in 0 until count) {
            val repoName = "${UT_REPO_NAME}_$i"
            val sequence = HashShardingUtils.shardingSequenceFor(repoName, SHARDING_COUNT)
            val oldCollectionName = "${OLD_COLLECTION_NAME_PREFIX}_$sequence"
            val blockNode = mongoTemplate.insert(buildBlockNode(repoName = repoName), oldCollectionName)
            if (i % 5 == 0) {
                val newSequence = HashShardingUtils.shardingSequenceFor(listOf(UT_PROJECT_ID, repoName), SHARDING_COUNT)
                mongoTemplate.insert(blockNode, "${NEW_COLLECTION_NAME_PREFIX}_$newSequence")
            }
        }
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
