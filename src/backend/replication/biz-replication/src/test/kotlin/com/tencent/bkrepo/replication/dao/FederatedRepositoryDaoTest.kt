package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
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
    private val projectId2 = "test-project-2"
    private val repoName2 = "test-repo-2"
    private val federationId2 = "test-federation-2"
    private val clusterId2 = "test-cluster-2"

    @BeforeEach
    fun setUp() {
        // 清理所有测试数据
        federatedRepositoryDao.remove(Query())

        // 创建主要测试数据
        val federatedRepository = createTestRepository(
            projectId = projectId,
            repoName = repoName,
            federationId = federationId,
            clusterId = clusterId,
            name = "test-federation-repo"
        )
        federatedRepositoryDao.save(federatedRepository)

        // 创建额外的测试数据用于查询测试
        val federatedRepository2 = createTestRepository(
            projectId = projectId2,
            repoName = repoName2,
            federationId = federationId2,
            clusterId = clusterId2,
            name = "test-federation-repo-2"
        )
        federatedRepositoryDao.save(federatedRepository2)

        // 创建同名但不同项目的仓库
        val federatedRepository3 = createTestRepository(
            projectId = projectId2,
            repoName = repoName,
            federationId = "federation-3",
            clusterId = "cluster-3",
            name = "test-federation-repo"
        )
        federatedRepositoryDao.save(federatedRepository3)
    }

    private fun createTestRepository(
        projectId: String,
        repoName: String,
        federationId: String,
        clusterId: String,
        name: String,
        isFullSyncing: Boolean = false
    ): TFederatedRepository {
        return TFederatedRepository(
            createdBy = "test-user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "test-user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federationId = federationId,
            name = name,
            federatedClusters = listOf(
                FederatedCluster(
                    projectId = projectId,
                    repoName = repoName,
                    clusterId = clusterId,
                    enabled = true,
                    taskId = "task-1"
                )
            ),
            isFullSyncing = isFullSyncing
        )
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

    // ========== 查询功能测试 ==========

    @Test
    fun `test findByProjectIdAndRepoName - basic query`() {
        val results = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName)
        
        assertEquals(1, results.size)
        val repository = results.first()
        assertEquals(projectId, repository.projectId)
        assertEquals(repoName, repository.repoName)
        assertEquals(federationId, repository.federationId)
        assertEquals("test-federation-repo", repository.name)
    }

    @Test
    fun `test findByProjectIdAndRepoName - with federationId filter`() {
        val results = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
        
        assertEquals(1, results.size)
        assertEquals(federationId, results.first().federationId)
    }

    @Test
    fun `test findByProjectIdAndRepoName - with wrong federationId filter`() {
        val results = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, "wrong-federation")
        
        assertEquals(0, results.size)
    }

    @Test
    fun `test findByProjectIdAndRepoName - non-existent repository`() {
        val results = federatedRepositoryDao.findByProjectIdAndRepoName("non-exist", "non-exist")
        
        assertEquals(0, results.size)
    }

    @Test
    fun `test findByName - single result`() {
        val results = federatedRepositoryDao.findByName("test-federation-repo-2")
        
        assertEquals(1, results.size)
        assertEquals(projectId2, results.first().projectId)
        assertEquals(repoName2, results.first().repoName)
    }

    @Test
    fun `test findByName - multiple results with same name`() {
        val results = federatedRepositoryDao.findByName("test-federation-repo")
        
        assertEquals(2, results.size)
        val projectIds = results.map { it.projectId }.toSet()
        assertTrue(projectIds.contains(projectId))
        assertTrue(projectIds.contains(projectId2))
    }

    @Test
    fun `test findByName - non-existent name`() {
        val results = federatedRepositoryDao.findByName("non-existent-name")
        
        assertEquals(0, results.size)
    }

    // ========== 删除功能测试 ==========

    @Test
    fun `test deleteByProjectIdAndRepoName - success`() {
        // 验证数据存在
        val beforeDelete = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
        assertEquals(1, beforeDelete.size)

        // 执行删除
        federatedRepositoryDao.deleteByProjectIdAndRepoName(projectId, repoName, federationId)

        // 验证数据已删除
        val afterDelete = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
        assertEquals(0, afterDelete.size)
    }

    @Test
    fun `test deleteByProjectIdAndRepoName - non-existent repository`() {
        // 删除不存在的仓库不应该抛出异常
        federatedRepositoryDao.deleteByProjectIdAndRepoName("non-exist", "non-exist", "non-exist")
        
        // 原有数据应该仍然存在
        val results = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
        assertEquals(1, results.size)
    }

    @Test
    fun `test deleteByProjectIdAndRepoName - partial match`() {
        // 只有完全匹配的记录才会被删除
        federatedRepositoryDao.deleteByProjectIdAndRepoName(projectId, repoName, "wrong-federation")
        
        // 原有数据应该仍然存在
        val results = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
        assertEquals(1, results.size)
    }

    // ========== 集群更新功能测试 ==========

    @Test
    fun `test updateFederatedClusters - success`() {
        val newClusters = listOf(
            FederatedCluster(
                projectId = projectId,
                repoName = repoName,
                clusterId = "new-cluster-1",
                enabled = true,
                taskId = "new-task-1"
            ),
            FederatedCluster(
                projectId = projectId,
                repoName = repoName,
                clusterId = "new-cluster-2",
                enabled = false,
                taskId = "new-task-2"
            )
        )

        // 执行更新
        federatedRepositoryDao.updateFederatedClusters(projectId, repoName, federationId, newClusters)

        // 验证更新结果
        val repository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertEquals(2, repository.federatedClusters.size)
        
        val cluster1 = repository.federatedClusters.find { it.clusterId == "new-cluster-1" }
        assertNotNull(cluster1)
        assertTrue(cluster1!!.enabled)
        assertEquals("new-task-1", cluster1.taskId)
        
        val cluster2 = repository.federatedClusters.find { it.clusterId == "new-cluster-2" }
        assertNotNull(cluster2)
        assertFalse(cluster2!!.enabled)
        assertEquals("new-task-2", cluster2.taskId)
    }

    @Test
    fun `test updateFederatedClusters - empty list`() {
        // 更新为空列表
        federatedRepositoryDao.updateFederatedClusters(projectId, repoName, federationId, emptyList())

        // 验证更新结果
        val repository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertEquals(0, repository.federatedClusters.size)
    }

    @Test
    fun `test updateFederatedClusters - non-existent repository`() {
        val newClusters = listOf(
            FederatedCluster(
                projectId = "non-exist",
                repoName = "non-exist",
                clusterId = "cluster",
                enabled = true
            )
        )

        // 更新不存在的仓库不应该抛出异常
        federatedRepositoryDao.updateFederatedClusters("non-exist", "non-exist", "non-exist", newClusters)
        
        // 原有数据应该不受影响
        val repository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertEquals(1, repository.federatedClusters.size)
    }

    // ========== 边界情况和异常测试 ==========

    @Test
    fun `test sync operations with null values`() {
        // 创建一个没有同步时间的仓库
        val repository = createTestRepository(
            projectId = "null-test",
            repoName = "null-test",
            federationId = "null-test",
            clusterId = "null-test",
            name = "null-test"
        )
        federatedRepositoryDao.save(repository)

        // 测试开始同步
        assertTrue(federatedRepositoryDao.updateFullSyncStart("null-test", "null-test", "null-test"))
        assertTrue(federatedRepositoryDao.isFullSyncing("null-test", "null-test", "null-test"))

        // 测试结束同步
        federatedRepositoryDao.updateFullSyncEnd("null-test", "null-test", "null-test")
        assertFalse(federatedRepositoryDao.isFullSyncing("null-test", "null-test", "null-test"))
    }

    @Test
    fun `test multiple repositories with same project and repo but different federation`() {
        // 创建同一个项目和仓库的不同联邦配置
        val repository1 = createTestRepository(
            projectId = "multi-test",
            repoName = "multi-test",
            federationId = "federation-1",
            clusterId = "cluster-1",
            name = "multi-federation-1"
        )
        val repository2 = createTestRepository(
            projectId = "multi-test",
            repoName = "multi-test",
            federationId = "federation-2",
            clusterId = "cluster-2",
            name = "multi-federation-2"
        )
        
        federatedRepositoryDao.save(repository1)
        federatedRepositoryDao.save(repository2)

        // 测试查询所有相关仓库
        val allRepos = federatedRepositoryDao.findByProjectIdAndRepoName("multi-test", "multi-test")
        assertEquals(2, allRepos.size)

        // 测试查询特定联邦
        val specificRepo = federatedRepositoryDao.findByProjectIdAndRepoName("multi-test", "multi-test", "federation-1")
        assertEquals(1, specificRepo.size)
        assertEquals("federation-1", specificRepo.first().federationId)

        // 测试独立的同步状态
        assertTrue(federatedRepositoryDao.updateFullSyncStart("multi-test", "multi-test", "federation-1"))
        assertTrue(federatedRepositoryDao.isFullSyncing("multi-test", "multi-test", "federation-1"))
        assertFalse(federatedRepositoryDao.isFullSyncing("multi-test", "multi-test", "federation-2"))

        // 测试独立删除
        federatedRepositoryDao.deleteByProjectIdAndRepoName("multi-test", "multi-test", "federation-1")
        val remainingRepos = federatedRepositoryDao.findByProjectIdAndRepoName("multi-test", "multi-test")
        assertEquals(1, remainingRepos.size)
        assertEquals("federation-2", remainingRepos.first().federationId)
    }

    @Test
    fun `test lastModifiedDate is updated correctly`() {
        val beforeUpdate = LocalDateTime.now()
        
        // 等待一小段时间确保时间戳不同
        Thread.sleep(10)
        
        // 执行更新操作
        assertTrue(federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId))
        
        // 验证时间戳已更新
        val repository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertTrue(repository.lastModifiedDate.isAfter(beforeUpdate))
        
        val afterSyncStart = repository.lastModifiedDate
        Thread.sleep(10)
        
        // 结束同步
        federatedRepositoryDao.updateFullSyncEnd(projectId, repoName, federationId)
        
        // 验证时间戳再次更新
        val updatedRepository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).first()
        assertTrue(updatedRepository.lastModifiedDate.isAfter(afterSyncStart))
    }
}