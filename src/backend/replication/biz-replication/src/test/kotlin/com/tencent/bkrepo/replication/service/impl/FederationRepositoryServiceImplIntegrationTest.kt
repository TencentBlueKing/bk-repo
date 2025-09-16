package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryUpdateRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.impl.federation.FederationSyncManager
import com.tencent.bkrepo.replication.service.impl.federation.FederationTaskManager
import com.tencent.bkrepo.replication.service.impl.federation.LocalFederationManager
import com.tencent.bkrepo.replication.service.impl.federation.RemoteFederationManager
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class FederationRepositoryServiceImplIntegrationTest {

    @MockK
    private lateinit var localFederationManager: LocalFederationManager

    @MockK
    private lateinit var remoteFederationManager: RemoteFederationManager

    @MockK
    private lateinit var federationTaskManager: FederationTaskManager

    @MockK
    private lateinit var federationSyncManager: FederationSyncManager

    @MockK
    private lateinit var clusterNodeService: ClusterNodeService

    @InjectMockKs
    private lateinit var federationRepositoryService: FederationRepositoryServiceImpl

    private val projectId = "integration-project"
    private val repoName = "integration-repo"
    private val federationId = "integration-federation-id"
    private val clusterId1 = "cluster-1"
    private val clusterId2 = "cluster-2"
    private val clusterId3 = "cluster-3"
    private val federationName = "integration-federation"

    private lateinit var cluster1: ClusterNodeInfo
    private lateinit var cluster2: ClusterNodeInfo
    private lateinit var cluster3: ClusterNodeInfo
    private lateinit var federatedCluster1: FederatedCluster
    private lateinit var federatedCluster2: FederatedCluster
    private lateinit var federatedCluster3: FederatedCluster

    @BeforeEach
    fun setUp() {
        cluster1 = ClusterNodeInfo(
            id = clusterId1,
            name = "cluster-1",
            url = "http://cluster1.com",
            username = "xxxx",
            password = "xxxx",
            certificate = null,
            accessKey = null,
            secretKey = null,
            appId = null,
            createdBy = "test-user",
            createdDate = LocalDateTime.now().toString(),
            lastModifiedBy = "test-user",
            lastModifiedDate = LocalDateTime.now().toString(),
            detectType = null,
            lastReportTime = null,
            status = ClusterNodeStatus.HEALTHY,
            errorReason = null,
            type = ClusterNodeType.STANDALONE
        )

        cluster2 = cluster1.copy(id = clusterId2, name = "cluster-2", url = "http://cluster2.com")
        cluster3 = cluster1.copy(id = clusterId3, name = "cluster-3", url = "http://cluster3.com")

        federatedCluster1 = FederatedCluster(
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId1,
            enabled = true,
            taskId = "task-1"
        )

        federatedCluster2 = FederatedCluster(
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId2,
            enabled = true,
            taskId = "task-2"
        )

        federatedCluster3 = FederatedCluster(
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId3,
            enabled = true,
            taskId = "task-3"
        )
    }

    @Test
    fun `test complete federation lifecycle - create, update, delete`() {
        // Phase 1: 创建联邦仓库
        val createRequest = FederatedRepositoryCreateRequest(
            name = federationName,
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId1,
            federatedClusters = listOf(federatedCluster1, federatedCluster2)
        )

        every { clusterNodeService.getByClusterId(clusterId1) } returns cluster1
        every { localFederationManager.isFederationNameExists(federationName) } returns false
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(
            clusterId1 to cluster1,
            clusterId2 to cluster2
        )
        every {
            remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any())
        } just runs
        every { remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any()) } just runs
        every { federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any()) } returns "task-id"
        every { localFederationManager.saveFederationRepository(any()) } just runs

        val createdFederationId = federationRepositoryService.createFederationRepository(createRequest)
        assertTrue(createdFederationId.isNotEmpty())

        // Phase 2: 更新联邦仓库 - 添加新集群
        val existingFederation = TFederatedRepository(
            createdBy = "test-user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "test-user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId1,
            federationId = createdFederationId,
            name = federationName,
            federatedClusters = listOf(federatedCluster1, federatedCluster2),
            isFullSyncing = false
        )

        val updateRequest = FederatedRepositoryUpdateRequest(
            projectId = projectId,
            repoName = repoName,
            federationId = createdFederationId,
            federatedClusters = listOf(federatedCluster1, federatedCluster2, federatedCluster3)
        )

        every {
            localFederationManager.getFederationRepository(projectId, repoName, createdFederationId)
        } returns existingFederation
        every { clusterNodeService.getByClusterId(clusterId3) } returns cluster3
        every {
            federationTaskManager.getClusterInfoMap(listOf(federatedCluster3))
        } returns mapOf(clusterId3 to cluster3)
        every { localFederationManager.updateFederationClusters(any(), any(), any(), any()) } just runs

        val updateResult = federationRepositoryService.updateFederationRepository(updateRequest)
        assertTrue(updateResult)

        // Phase 3: 移除一个集群
        val updatedFederation = existingFederation.copy(
            federatedClusters = listOf(federatedCluster1, federatedCluster2, federatedCluster3)
        )

        every {
            localFederationManager.getFederationRepository(projectId, repoName, createdFederationId)
        } returns updatedFederation
        every { localFederationManager.getClusterIdByName("cluster-2") } returns clusterId2
        every { remoteFederationManager.deleteRemoteFederationConfig(any(), any()) } just runs
        every {
            remoteFederationManager.deleteRemoteConfigForTargetCluster(any(), any(), any(), any(), any())
        } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every { localFederationManager.updateFederationClusters(any(), any(), any(), any()) } just runs

        federationRepositoryService.removeClusterFromFederation(
            projectId, repoName, createdFederationId, "cluster-2", projectId, repoName, true
        )

        // Phase 4: 删除整个联邦配置
        val finalFederation = updatedFederation.copy(
            federatedClusters = listOf(federatedCluster1, federatedCluster3)
        )

        every {
            localFederationManager.getFederationRepository(projectId, repoName, createdFederationId)
        } returns finalFederation
        every { localFederationManager.deleteConfig(projectId, repoName, createdFederationId) } just runs

        federationRepositoryService.deleteFederationRepositoryConfig(
            projectId, repoName, createdFederationId, true
        )

        // 验证所有阶段的调用
        verify { localFederationManager.saveFederationRepository(any()) }
        verify(atLeast = 1) { localFederationManager.updateFederationClusters(any(), any(), any(), any()) }
        verify { localFederationManager.deleteConfig(projectId, repoName, createdFederationId) }
    }

    @Test
    fun `test concurrent federation operations - error handling`() {
        // 模拟并发创建相同名称的联邦仓库
        val request1 = FederatedRepositoryCreateRequest(
            name = federationName,
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId1,
            federatedClusters = listOf(federatedCluster1)
        )

        val request2 = FederatedRepositoryCreateRequest(
            name = federationName,
            projectId = "another-project",
            repoName = "another-repo",
            clusterId = clusterId2,
            federatedClusters = listOf(federatedCluster2)
        )

        // 第一个请求成功
        every { clusterNodeService.getByClusterId(clusterId1) } returns cluster1
        every { localFederationManager.isFederationNameExists(federationName) } returns false
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(clusterId1 to cluster1)
        every {
            remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any())
        } just runs
        every { remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any()) } just runs
        every { federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any()) } returns "task-id"
        every { localFederationManager.saveFederationRepository(any()) } just runs

        val result1 = federationRepositoryService.createFederationRepository(request1)
        assertTrue(result1.isNotEmpty())

        // 第二个请求失败（名称已存在）
        every { clusterNodeService.getByClusterId(clusterId2) } returns cluster2
        every { localFederationManager.isFederationNameExists(federationName) } returns true

        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.createFederationRepository(request2)
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_NAME_EXISTS, exception.messageCode)
    }

    @Test
    fun `test federation update with complex cluster changes`() {
        // 初始状态：3个集群
        val initialFederation = TFederatedRepository(
            createdBy = "test-user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "test-user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId1,
            federationId = federationId,
            name = federationName,
            federatedClusters = listOf(federatedCluster1, federatedCluster2, federatedCluster3),
            isFullSyncing = false
        )

        // 更新：移除cluster2，添加新的cluster4，保留cluster1和cluster3
        val clusterId4 = "cluster-4"
        val cluster4 = cluster1.copy(id = clusterId4, name = "cluster-4", url = "http://cluster4.com")
        val federatedCluster4 = FederatedCluster(
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId4,
            enabled = true,
            taskId = "task-4"
        )

        val updateRequest = FederatedRepositoryUpdateRequest(
            projectId = projectId,
            repoName = repoName,
            federationId = federationId,
            federatedClusters = listOf(federatedCluster1, federatedCluster3, federatedCluster4)
        )

        every {
            localFederationManager.getFederationRepository(projectId, repoName, federationId)
        } returns initialFederation
        every { clusterNodeService.getByClusterId(clusterId1) } returns cluster1
        every { clusterNodeService.getByClusterId(clusterId2) } returns cluster2
        every { clusterNodeService.getByClusterId(clusterId4) } returns cluster4

        // Mock getClusterIdByName for removeClusterFromFederation
        every { localFederationManager.getClusterIdByName("cluster-2") } returns clusterId2

        // Mock 移除cluster2的操作
        every { remoteFederationManager.deleteRemoteFederationConfig(any(), any()) } just runs
        every {
            remoteFederationManager.deleteRemoteConfigForTargetCluster(any(), any(), any(), any(), any())
        } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs

        // Mock 添加cluster4的操作
        every {
            federationTaskManager.getClusterInfoMap(listOf(federatedCluster4))
        } returns mapOf(clusterId4 to cluster4)
        every {
            remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any())
        } just runs
        every { remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any()) } just runs
        every { federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any()) } returns "task-4"

        every { localFederationManager.updateFederationClusters(any(), any(), any(), any()) } just runs

        val result = federationRepositoryService.updateFederationRepository(updateRequest)
        assertTrue(result)

        // 验证移除和添加操作都被调用
        verify { remoteFederationManager.deleteRemoteFederationConfig(any(), any()) }
        verify { federationTaskManager.deleteFederationTasks(any()) }
        verify { remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any()) }
        verify { remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any()) }
        verify { federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any()) }
        verify { localFederationManager.updateFederationClusters(any(), any(), any(), any()) }
    }

    @Test
    fun `test federation full sync with error recovery`() {
        // 模拟全量同步过程中的错误恢复
        every {
            federationSyncManager.executeFullSync(projectId, repoName, federationId)
        } throws RuntimeException("Sync failed")

        // 第一次同步失败
        assertThrows(RuntimeException::class.java) {
            federationRepositoryService.fullSyncFederationRepository(projectId, repoName, federationId)
        }

        // 修复问题后重新同步成功
        every { federationSyncManager.executeFullSync(projectId, repoName, federationId) } just runs

        federationRepositoryService.fullSyncFederationRepository(projectId, repoName, federationId)

        verify(exactly = 2) { federationSyncManager.executeFullSync(projectId, repoName, federationId) }
    }

    @Test
    fun `test edge case - remove last cluster triggers full deletion`() {
        // 只有一个集群的联邦仓库
        val singleClusterFederation = TFederatedRepository(
            createdBy = "test-user",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "test-user",
            lastModifiedDate = LocalDateTime.now(),
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId1,
            federationId = federationId,
            name = federationName,
            federatedClusters = listOf(federatedCluster1),
            isFullSyncing = false
        )

        every { localFederationManager.getClusterIdByName("cluster-1") } returns clusterId1
        every {
            localFederationManager.getFederationRepository(projectId, repoName, federationId)
        } returns singleClusterFederation
        every { remoteFederationManager.deleteRemoteFederationConfig(any(), any()) } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every { localFederationManager.deleteConfig(projectId, repoName, federationId) } just runs

        federationRepositoryService.removeClusterFromFederation(
            projectId, repoName, federationId, "cluster-1", projectId, repoName,true
        )

        // 验证触发了完整删除而不是部分更新
        verify { localFederationManager.deleteConfig(projectId, repoName, federationId) }
        verify(exactly = 0) { localFederationManager.updateFederationClusters(any(), any(), any(), any()) }
    }
}