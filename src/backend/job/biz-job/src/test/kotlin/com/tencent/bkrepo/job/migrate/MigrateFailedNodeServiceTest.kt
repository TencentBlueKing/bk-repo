package com.tencent.bkrepo.job.migrate

import com.tencent.bkrepo.job.UT_MD5
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeAutoFixStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

@DisplayName("迁移失败节点服务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    MigrateFailedNodeService::class,
    MigrateFailedNodeDao::class,
)
class MigrateFailedNodeServiceTest @Autowired constructor(
    private val migrateFailedNodeService: MigrateFailedNodeService,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
) {
    @MockBean
    private lateinit var autoFixStrategy: MigrateFailedNodeAutoFixStrategy

    @BeforeAll
    fun beforeAll() {
        whenever(autoFixStrategy.fix(any())).thenReturn(true)
    }

    @BeforeEach
    fun beforeEach() {
        migrateFailedNodeDao.remove(Query())
    }

    @Test
    fun testRemoveFailedNode() {
        // remove repo failed node
        insertFailedNode("/a/b/c.txt")
        insertFailedNode("/a/b/d.txt")
        assertEquals(2, migrateFailedNodeDao.count(Query()))
        migrateFailedNodeService.removeFailedNode(UT_PROJECT_ID, UT_REPO_NAME, null)
        assertEquals(0, migrateFailedNodeDao.count(Query()))

        // remove failed node
        insertFailedNode("/a/b/c.txt")
        assertEquals(1, migrateFailedNodeDao.count(Query()))
        migrateFailedNodeService.removeFailedNode(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c.txt")
        assertEquals(0, migrateFailedNodeDao.count(Query()))
    }

    @Test
    fun testResetRetryCount() {
        insertFailedNode("/a/b/c.txt")
        insertFailedNode("/a/b/d.txt")

        // reset repo nodes
        var node1 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        var node2 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(1, node1.retryTimes)
        assertEquals(1, node2.retryTimes)
        migrateFailedNodeService.resetRetryCount(UT_PROJECT_ID, UT_REPO_NAME, null)
        assertEquals(0, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(0, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
        migrateFailedNodeDao.resetMigrating(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c.txt")
        migrateFailedNodeDao.resetMigrating(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/d.txt")

        // reset single node
        node1 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        node2 = migrateFailedNodeDao.findOneToRetry(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(1, node1.retryTimes)
        assertEquals(1, node2.retryTimes)
        migrateFailedNodeService.resetRetryCount(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c.txt")
        assertEquals(0, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(1, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
    }

    @Test
    fun testAutoFix() {
        val node1 = insertFailedNode("/a/b/c.txt")
        val node2 = insertFailedNode("/a/b/d.txt")
        migrateFailedNodeDao.updateMulti(Query(), Update().set(TMigrateFailedNode::retryTimes.name, 3))
        assertEquals(3, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(3, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)

        migrateFailedNodeService.autoFix(UT_PROJECT_ID, UT_REPO_NAME)
        assertEquals(0, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(0, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
    }

    private fun insertFailedNode(fullPath: String = "/a/b/c.txt"): TMigrateFailedNode {
        val now = LocalDateTime.now()
        return migrateFailedNodeDao.insert(
            TMigrateFailedNode(
                id = null,
                createdDate = now,
                lastModifiedDate = now,
                nodeId = "",
                taskId = "",
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = fullPath,
                sha256 = UT_SHA256,
                size = 1000L,
                md5 = UT_MD5,
                retryTimes = 0,
            )
        )
    }
}
