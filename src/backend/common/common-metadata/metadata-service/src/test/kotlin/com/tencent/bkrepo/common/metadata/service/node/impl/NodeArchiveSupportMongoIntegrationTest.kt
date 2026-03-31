package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.model.TSeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.SeparationContent
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRestoreRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation.group
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.LocalDateTime

@DataMongoTest
@Import(NodeDao::class, RepositoryDao::class, SeparationNodeDao::class, NodeArchiveSupportMongoTestConfig::class)
@TestPropertySource(
    locations = ["classpath:bootstrap-ut.properties"],
    properties = ["sharding.count=256"],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("NodeArchiveSupport 嵌入式 Mongo")
class NodeArchiveSupportMongoIntegrationTest @Autowired constructor(
    private val nodeDao: NodeDao,
    private val repositoryDao: RepositoryDao,
    private val separationNodeDao: SeparationNodeDao,
    private val separationTaskService: SeparationTaskService,
    private val mongoTemplate: MongoTemplate,
) {

    private val stamp = LocalDateTime.of(2024, 2, 1, 12, 0)
    private val sepDate = LocalDateTime.of(2024, 3, 1, 0, 0)
    private val oldAccess = LocalDateTime.now().minusDays(400)
    private val archiveClient = mockk<ArchiveClient>(relaxed = true)

    @BeforeEach
    fun clean() {
        mongoTemplate.dropCollection("repository")
        mongoTemplate.dropCollection("separation_task")
        mongoTemplate.collectionNames.filter { it.startsWith("separation_node_") }
            .forEach { mongoTemplate.dropCollection(it) }
        mongoTemplate.collectionNames.filter { it.startsWith("node_") }
            .forEach { mongoTemplate.dropCollection(it) }
    }

    @Test
    @DisplayName("getArchivableSize：仓库命中 specialSeparateRepos 时热表+冷表聚合")
    fun getArchivableSize_whenSeparationEnabled_sumsHotAndCold() {
        saveSeparateTaskHint()
        nodeDao.insert(archivableHotNode(size = 50L))
        separationNodeDao.save(archivableColdNode(size = 30L))
        val cfg = DataSeparationConfig(
            keepDays = Duration.ofDays(30),
            specialSeparateRepos = mutableListOf("p/r"),
        )
        val support = NodeArchiveSupport(
            nodeBaseStub(),
            archiveClient,
            cfg,
            separationNodeDao,
            separationTaskService,
        )
        assertEquals(80L, support.getArchivableSize("p", "r", days = 7, size = null))
    }

    @Test
    @DisplayName("getArchivableSize：未配置 specialSeparateRepos 时不计冷表（isSeparationEnabled=false）")
    fun getArchivableSize_whenSeparationDisabled_ignoresCold() {
        saveSeparateTaskHint()
        nodeDao.insert(archivableHotNode(size = 50L))
        separationNodeDao.save(archivableColdNode(size = 999L))
        val cfg = DataSeparationConfig(specialSeparateRepos = mutableListOf())
        val support = NodeArchiveSupport(
            nodeBaseStub(),
            archiveClient,
            cfg,
            separationNodeDao,
            separationTaskService,
        )
        assertEquals(50L, support.getArchivableSize("p", "r", days = 7, size = null))
    }

    @Test
    @DisplayName("getArchivableSize：仓库不在配置中时不计冷表")
    fun getArchivableSize_whenRepoNotMatched_ignoresCold() {
        saveSeparateTaskHint()
        nodeDao.insert(archivableHotNode(size = 10L))
        separationNodeDao.save(archivableColdNode(size = 500L))
        val cfg = DataSeparationConfig(specialSeparateRepos = mutableListOf("p/other"))
        val support = NodeArchiveSupport(
            nodeBaseStub(),
            archiveClient,
            cfg,
            separationNodeDao,
            separationTaskService,
        )
        assertEquals(10L, support.getArchivableSize("p", "r", days = 7, size = null))
    }

    @Test
    @DisplayName("getArchivableSize：repoName=null 时按项目模式 p/* 启用冷表")
    fun getArchivableSize_projectPattern_repoNameNull_includesCold() {
        saveSeparateTaskHint()
        nodeDao.insert(archivableHotNode(size = 20L))
        separationNodeDao.save(archivableColdNode(size = 15L))
        val cfg = DataSeparationConfig(specialSeparateRepos = mutableListOf("p/*"))
        val support = NodeArchiveSupport(
            nodeBaseStub(),
            archiveClient,
            cfg,
            separationNodeDao,
            separationTaskService,
        )
        assertEquals(35L, support.getArchivableSize("p", null, days = 7, size = null))
    }

    @Test
    @DisplayName("restoreNode(单路径)：命中冷表 archived 节点时写入 RESTORE_ARCHIVED 任务")
    fun restoreNode_singlePath_createsRestoreArchivedTask() {
        saveSeparateTaskHint()
        separationNodeDao.save(
            coldArchivedFile("/a.txt", archived = true).apply { separationDate = sepDate },
        )
        val cfg = DataSeparationConfig(specialSeparateRepos = mutableListOf("p/r"))
        val support = NodeArchiveSupport(
            nodeBaseStub(),
            archiveClient,
            cfg,
            separationNodeDao,
            separationTaskService,
        )
        support.restoreNode(NodeArchiveRequest("p", "r", "/a.txt", "u"))
        val tasks = mongoTemplate.find(Query(), TSeparationTask::class.java)
            .filter { it.type == SeparationTaskServiceImpl.RESTORE_ARCHIVED }
        assertEquals(1, tasks.size)
        assertEquals("/a.txt", tasks.first().content.paths?.first()?.path)
    }

    @Test
    @DisplayName("restoreNode(批量)：冷表路径写入 RESTORE 任务并出现在返回列表")
    fun restoreNode_batch_addsColdPathsAndTasks() {
        saveSeparateTaskHint()
        repositoryDao.insert(testRepo())
        separationNodeDao.save(
            coldArchivedFile("/c1.txt", archived = false, compressed = true).apply { separationDate = sepDate },
        )
        val cfg = DataSeparationConfig(specialSeparateRepos = mutableListOf("p/r"))
        val support = NodeArchiveSupport(
            nodeBaseStub(),
            archiveClient,
            cfg,
            separationNodeDao,
            separationTaskService,
        )
        val paths = support.restoreNode(
            NodeArchiveRestoreRequest("p", "r", path = null, metadata = emptyMap(), limit = 10, operator = "u"),
        )
        assertTrue(paths.contains("/c1.txt"))
        val restoreTasks = mongoTemplate.find(Query(), TSeparationTask::class.java)
            .filter { it.type == SeparationTaskServiceImpl.RESTORE }
        assertEquals(1, restoreTasks.size)
    }

    private fun nodeBaseStub(): NodeBaseService {
        val base = mockk<NodeBaseService>(relaxed = true)
        every { base.nodeDao } returns nodeDao
        every { base.repositoryDao } returns repositoryDao
        every { base.aggregateComputeSize(any()) } answers {
            val c = firstArg<Criteria>()
            val aggregation = newAggregation(
                match(c),
                group().sum(TNode::size.name).`as`(NodeSizeInfo::size.name),
            )
            val res = nodeDao.aggregate(aggregation, HashMap::class.java)
            res.mappedResults.firstOrNull()?.get(NodeSizeInfo::size.name) as? Long ?: 0L
        }
        return base
    }

    private fun saveSeparateTaskHint() {
        mongoTemplate.save(
            TSeparationTask(
                projectId = "p",
                repoName = "r",
                createdBy = "u",
                createdDate = stamp,
                lastModifiedBy = "u",
                lastModifiedDate = stamp,
                separationDate = sepDate,
                content = SeparationContent(),
                type = SeparationTaskServiceImpl.SEPARATE,
            ),
        )
    }

    private fun archivableHotNode(size: Long) = TNode(
        folder = false,
        path = "/",
        name = "h.bin",
        fullPath = "/h.bin",
        size = size,
        sha256 = "ab" + "0".repeat(62),
        projectId = "p",
        repoName = "r",
        createdBy = "u",
        createdDate = stamp,
        lastModifiedBy = "u",
        lastModifiedDate = stamp,
        lastAccessDate = oldAccess,
        archived = false,
        metadata = mutableListOf(),
    )

    private fun archivableColdNode(size: Long) = TSeparationNode(
        createdBy = "u",
        createdDate = stamp,
        lastModifiedBy = "u",
        lastModifiedDate = stamp,
        lastAccessDate = oldAccess,
        folder = false,
        path = "/",
        name = "cold.bin",
        fullPath = "/cold.bin",
        size = size,
        sha256 = "cd" + "0".repeat(62),
        projectId = "p",
        repoName = "r",
        archived = false,
    ).apply { separationDate = sepDate }

    private fun coldArchivedFile(fullPath: String, archived: Boolean, compressed: Boolean = false) = TSeparationNode(
        createdBy = "u",
        createdDate = stamp,
        lastModifiedBy = "u",
        lastModifiedDate = stamp,
        folder = false,
        path = "/",
        name = fullPath.trimStart('/'),
        fullPath = fullPath,
        size = 1L,
        sha256 = "ef" + "0".repeat(62),
        projectId = "p",
        repoName = "r",
        archived = archived,
        compressed = compressed,
    )

    private fun testRepo() = TRepository(
        createdBy = "u",
        createdDate = stamp,
        lastModifiedBy = "u",
        lastModifiedDate = stamp,
        name = "r",
        type = RepositoryType.GENERIC,
        category = RepositoryCategory.LOCAL,
        public = true,
        configuration = "{}",
        projectId = "p",
        credentialsKey = "ck",
    )
}
