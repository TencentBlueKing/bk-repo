package com.tencent.bkrepo.job.migrate

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.UT_USER
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeAutoFixStrategy
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeFixer
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.insertFailedNode
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
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

@DisplayName("迁移失败节点服务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    MigrateFailedNodeService::class,
    MigrateFailedNodeDao::class,
    MigrateRepoStorageTaskDao::class,
    MigrateFailedNodeFixer::class,
)
class MigrateFailedNodeServiceTest @Autowired constructor(
    private val migrateFailedNodeService: MigrateFailedNodeService,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
) {
    @Autowired
    private lateinit var migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao

    @MockBean
    private lateinit var autoFixStrategy: MigrateFailedNodeAutoFixStrategy

    @BeforeEach
    fun beforeEach() {
        whenever(autoFixStrategy.fix(any())).thenReturn(true)
        migrateFailedNodeDao.remove(Query())
        migrateRepoStorageTaskDao.remove(Query())
    }

    @Test
    fun testRemoveFailedNode() {
        // remove repo failed node
        migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")
        assertEquals(2, migrateFailedNodeDao.count(Query()))
        migrateFailedNodeService.removeFailedNode(UT_PROJECT_ID, UT_REPO_NAME, null)
        assertEquals(0, migrateFailedNodeDao.count(Query()))

        // remove failed node
        migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        assertEquals(1, migrateFailedNodeDao.count(Query()))
        migrateFailedNodeService.removeFailedNode(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c.txt")
        assertEquals(0, migrateFailedNodeDao.count(Query()))
    }

    @Test
    fun testResetRetryCount() {
        migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")

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
        val node1 = migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        val node2 = migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")
        migrateFailedNodeDao.updateFirst(
            Query(Criteria.where(ID).isEqualTo(node1.id)),
            Update().set(TMigrateFailedNode::retryTimes.name, 2)
        )
        migrateFailedNodeDao.updateFirst(
            Query(Criteria.where(ID).isEqualTo(node2.id)),
            Update().set(TMigrateFailedNode::retryTimes.name, 3)
        )
        assertEquals(2, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(3, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)

        migrateFailedNodeService.autoFix(UT_PROJECT_ID, UT_REPO_NAME)
        Thread.sleep(1000L)
        assertEquals(2, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(0, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
    }

    @Test
    fun testAutoFixAll() {
        val now = LocalDateTime.now()
        migrateRepoStorageTaskDao.insert(
            TMigrateRepoStorageTask(
                id = null,
                createdBy = UT_USER,
                createdDate = now,
                lastModifiedBy = UT_USER,
                lastModifiedDate = now,
                startDate = now,
                totalCount = 2,
                migratedCount = 2,
                lastMigratedNodeId = "",
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                srcStorageKey = null,
                dstStorageKey = UT_STORAGE_CREDENTIALS_KEY,
                state = MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE.name,
            )
        )

        val node1 = migrateFailedNodeDao.insertFailedNode("/a/b/c.txt")
        val node2 = migrateFailedNodeDao.insertFailedNode("/a/b/d.txt")
        migrateFailedNodeDao.updateMulti(Query(), Update().set(TMigrateFailedNode::retryTimes.name, 3))
        assertEquals(3, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(3, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)

        migrateFailedNodeService.autoFix()
        Thread.sleep(1000L)
        assertEquals(0, migrateFailedNodeDao.findById(node1.id!!)!!.retryTimes)
        assertEquals(0, migrateFailedNodeDao.findById(node2.id!!)!!.retryTimes)
    }
}
