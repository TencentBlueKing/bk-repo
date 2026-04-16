package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.model.TSeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.SeparationContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DataMongoTest
@Import(
    SeparationDataServiceImpl::class,
    SeparationNodeDao::class,
    SeparationPackageDao::class,
    SeparationPackageVersionDao::class,
    SeparationMongoIntegrationTestConfig::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("SeparationDataServiceImpl 嵌入式 Mongo（无 Mockito）")
class SeparationDataServiceImplMongoIntegrationTest @Autowired constructor(
    private val service: SeparationDataServiceImpl,
    private val separationNodeDao: SeparationNodeDao,
    private val mongoTemplate: MongoTemplate,
) {

    private val dOld = LocalDateTime.of(2024, 1, 1, 0, 0)
    private val dNew = LocalDateTime.of(2024, 6, 1, 0, 0)
    private val stamp = LocalDateTime.of(2024, 1, 15, 12, 0)

    @BeforeEach
    fun clean() {
        mongoTemplate.dropCollection("separation_task")
        mongoTemplate.collectionNames
            .filter { it.startsWith("separation_node_") }
            .forEach { mongoTemplate.dropCollection(it) }
    }

    @Test
    @DisplayName("findNodeInfo 按降冷日期倒序命中较早分表")
    fun findNodeInfo_prefersFirstHitInDescendingDateOrder() {
        saveTask(dOld)
        saveTask(dNew)
        separationNodeDao.save(coldNode(dOld, "/a.txt"))

        val info = service.findNodeInfo("p", "r", "/a.txt")
        assertNotNull(info)
        assertEquals("/a.txt", info!!.fullPath)
    }

    @Test
    @DisplayName("findNodeInfo 无任务时返回 null")
    fun findNodeInfo_noTasks_returnsNull() {
        assertNull(service.findNodeInfo("p", "r", "/x"))
    }

    @Test
    @DisplayName("countColdNodes 多分表计数求和")
    fun countColdNodes_sumsAcrossShards() {
        saveTask(dOld)
        saveTask(dNew)
        repeat(3) { separationNodeDao.save(coldNode(dOld, "/o$it.txt")) }
        repeat(7) { separationNodeDao.save(coldNode(dNew, "/n$it.txt")) }
        assertEquals(10L, service.countColdNodes(baseQuery()))
    }

    @Test
    @DisplayName("searchColdNodes 跨分表 skip")
    fun searchColdNodes_skipSpansShards() {
        saveTask(dOld)
        saveTask(dNew)
        repeat(2) { separationNodeDao.save(coldNode(dNew, "/nx$it.txt")) }
        repeat(2) { separationNodeDao.save(coldNode(dOld, "/ox$it.txt")) }
        val out = service.searchColdNodes(baseQuery(), skip = 3, limit = 10)
        assertEquals(1, out.size)
    }

    private fun baseQuery(): Query = Query.query(
        Criteria.where(TSeparationNode::projectId.name).`is`("p")
            .and(TSeparationNode::repoName.name).`is`("r"),
    )

    private fun saveTask(separationDate: LocalDateTime) {
        mongoTemplate.save(
            TSeparationTask(
                projectId = "p",
                repoName = "r",
                createdBy = "u",
                createdDate = stamp,
                lastModifiedBy = "u",
                lastModifiedDate = stamp,
                separationDate = separationDate,
                content = SeparationContent(),
                type = SeparationTaskServiceImpl.SEPARATE,
            ),
        )
    }

    private fun coldNode(separationDate: LocalDateTime, fullPath: String) = TSeparationNode(
        createdBy = "u",
        createdDate = stamp,
        lastModifiedBy = "u",
        lastModifiedDate = stamp,
        folder = false,
        path = "/",
        name = fullPath.trimStart('/'),
        fullPath = fullPath,
        size = 1L,
        sha256 = "s",
        projectId = "p",
        repoName = "r",
    ).apply {
        this.separationDate = separationDate
    }
}
