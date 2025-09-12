package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DataMongoTest
@Import(FederatedRepositoryDao::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class FederatedRepositoryDaoTest {

    @Autowired
    private lateinit var federatedRepositoryDao: FederatedRepositoryDao

    private val projectId = "test-project"
    private val repoName = "test-repo"
    private val federationId = "test-federation"
    private val clusterId = "test-cluster"

    @BeforeEach
    fun setUp() {
        // 清理测试数据
        federatedRepositoryDao.deleteByProjectIdAndRepoName(projectId, repoName, federationId)

        // 创建测试数据
        val federatedRepository = TFederatedRepository(
            createdBy = "test-user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "test-user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federationId = federationId,
            name = "test-federation-repo",
            federatedClusters = listOf(
                FederatedCluster(
                    enabled = true,
                    projectId = projectId,
                    clusterId = clusterId,
                    repoName = repoName,
                    taskId = "task-1"
                )
            ),
            isFullSyncing = false
        )
        federatedRepositoryDao.save(federatedRepository)
    }

    @Test
    fun `test updateFullSyncStart - success`() {
        // 初始状态应该不在同步中
        assertFalse(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 开始同步应该成功
        assertTrue(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))

        // 现在应该在同步中
        assertTrue(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 验证时间戳已更新
        val repository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertNotNull(repository.lastFullSyncStartTime)
        assertTrue(repository.isFullSyncing)
    }

    @Test
    fun `test updateFullSyncStart - already syncing`() {
        // 先开始一次同步
        assertTrue(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))

        // 再次尝试开始同步应该失败
        assertFalse(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))

        // 状态应该仍然是同步中
        assertTrue(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))
    }

    @Test
    fun `test updateFullSyncEnd`() {
        // 先开始同步
        assertTrue(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))
        assertTrue(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 结束同步
        federatedRepositoryDao.updateFullSyncEnd(projectId, repoName, federationId)

        // 现在应该不在同步中
        assertFalse(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 验证时间戳已更新
        val repository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertNotNull(repository.lastFullSyncEndTime)
        assertFalse(repository.isFullSyncing)
    }

    @Test
    fun `test isFullSyncing - not exists`() {
        // 对不存在的仓库查询同步状态应该返回false
        assertFalse(federatedRepositoryDao.isFullSyncing("non-exist", "non-exist", "non-exist"))
    }

    @Test
    fun `test full sync lifecycle`() {
        val startTime = LocalDateTime.now()

        // 1. 初始状态：未同步
        assertFalse(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 2. 开始同步
        assertTrue(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))
        assertTrue(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 3. 验证开始时间
        val repository1 = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertNotNull(repository1.lastFullSyncStartTime)
        assertTrue(
            repository1.lastFullSyncStartTime!!.isAfter(startTime)
                || repository1.lastFullSyncStartTime!!.isEqual(startTime)
        )
        assertNull(repository1.lastFullSyncEndTime)

        // 4. 结束同步
        federatedRepositoryDao.updateFullSyncEnd(projectId, repoName, federationId)
        assertFalse(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 5. 验证结束时间
        val repository2 = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertNotNull(repository2.lastFullSyncEndTime)
        assertTrue(repository2.lastFullSyncEndTime!!.isAfter(repository2.lastFullSyncStartTime!!))
    }

    @Test
    fun `test concurrent sync prevention`() {
        // 模拟并发场景：两个线程同时尝试开始同步

        // 第一个线程成功开始同步
        assertTrue(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))

        // 第二个线程尝试开始同步应该失败
        assertFalse(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))

        // 只有第一个线程的同步状态生效
        assertTrue(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 结束同步后，新的同步可以开始
        federatedRepositoryDao.updateFullSyncEnd(projectId, repoName, federationId)
        assertFalse(federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId))

        // 现在可以重新开始同步
        assertTrue(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))
    }
}