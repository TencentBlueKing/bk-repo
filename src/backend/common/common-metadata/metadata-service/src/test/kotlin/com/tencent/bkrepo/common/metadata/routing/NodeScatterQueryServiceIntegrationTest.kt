package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.metadata.MetadataTestConfiguration
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.util.concurrent.Executors

/**
 * NodeScatterQueryService 集成测试（Spec §16.1）。
 *
 * 使用嵌入式 MongoDB 验证 Default/Heavy 过滤条件与跨集合结果合并。
 */
@DataMongoTest
@ContextConfiguration(classes = [MetadataTestConfiguration::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class NodeScatterQueryServiceIntegrationTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
) {

    private val routedProject = "projectA"
    private val unroutedProject = "projectB"
    private val collections = listOf(
        shardFor(unroutedProject),
        shardFor(routedProject),
    )
    private lateinit var registry: MongoRoutingRegistry
    private lateinit var service: NodeScatterQueryService

    @BeforeEach
    fun setUp() {
        collections.forEach { mongoTemplate.dropCollection(it) }
        registry = mock()
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA")),
        )
        whenever(registry.shardRoutedCollections("node")).thenReturn(emptySet())
        whenever(registry.shardsByInstance("node")).thenReturn(emptyMap())
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(mongoTemplate)
        service = NodeScatterQueryService(
            defaultTemplate = mongoTemplate,
            registry = registry,
            executor = Executors.newFixedThreadPool(2),
        )
    }

    @AfterEach
    fun tearDown() {
        collections.forEach { mongoTemplate.dropCollection(it) }
    }

    @Test
    fun `scatterFind applies Default NOT IN and Heavy IN filters on embedded database`() {
        insertNode("default-id", unroutedProject, shardFor(unroutedProject))
        insertNode("heavy-id", routedProject, shardFor(routedProject))

        val result = service.scatterFind(Query(), TNode::class.java, collections)

        assertEquals(setOf("default-id", "heavy-id"), result.mapNotNull { it.id }.toSet())
        assertEquals("projectB", result.first { it.id == "default-id" }.projectId)
        assertEquals("projectA", result.first { it.id == "heavy-id" }.projectId)
    }

    @Test
    fun `scatterFind merges nodes across collections on embedded database`() {
        insertNode("id-0", unroutedProject, shardFor(unroutedProject))
        insertNode("id-1", routedProject, shardFor(routedProject))

        val result = service.scatterFind(Query(), TNode::class.java, collections)

        assertEquals(listOf("id-0", "id-1"), result.mapNotNull { it.id })
    }

    @Test
    fun `pageBySha256 returns merged page from embedded database`() {
        val sha256 = "deadbeefcafebabe"
        insertNode("page-default", unroutedProject, shardFor(unroutedProject), sha256)
        insertNode("page-heavy", routedProject, shardFor(routedProject), sha256)

        val page = service.pageBySha256(sha256, PageRequest.of(0, 10), collections)

        assertEquals(2, page.totalElements)
        assertEquals(
            setOf("page-default", "page-heavy"),
            page.content.mapNotNull { it.id }.toSet(),
        )
    }

    private fun insertNode(
        id: String,
        projectId: String,
        collection: String,
        sha256: String = "abc",
    ) {
        val now = LocalDateTime.now()
        mongoTemplate.insert(
            TNode(
                id = id,
                createdBy = "test",
                createdDate = now,
                lastModifiedBy = "test",
                lastModifiedDate = now,
                folder = false,
                path = "/",
                name = "$id.txt",
                fullPath = "/$id.txt",
                size = 1L,
                sha256 = sha256,
                projectId = projectId,
                repoName = "repo",
            ),
            collection,
        )
    }

    private fun shardFor(projectId: String): String =
        "node_${HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)}"
}
