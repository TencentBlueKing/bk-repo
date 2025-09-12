package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FederationRepositoryServiceImplTest : FederationRepositoryServiceTestBase() {

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

    private lateinit var mockClusterNodeInfo: ClusterNodeInfo
    private lateinit var mockFederatedCluster: FederatedCluster
    private lateinit var mockTFederatedRepository: TFederatedRepository

    @BeforeEach
    fun setUp() {
        mockClusterNodeInfo = createTestClusterNodeInfo(TEST_CLUSTER_ID_1)
        mockFederatedCluster = createTestFederatedCluster(clusterId = TEST_CLUSTER_ID_1)
        mockTFederatedRepository = createTestTFederatedRepository(
            clusterId = TEST_CLUSTER_ID_1,
            federatedClusters = listOf(mockFederatedCluster)
        )
    }

    @Test
    fun `test createFederationRepository - success`() {
        // Given
        val request = createTestFederatedRepositoryCreateRequest(
            federatedClusters = listOf(mockFederatedCluster)
        )

        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns mockClusterNodeInfo
        every { localFederationManager.isFederationNameExists(TEST_FEDERATION_NAME) } returns false
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(
            TEST_CLUSTER_ID_1 to mockClusterNodeInfo
        )
        every {
            remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any())
        } returns "task-id"
        every { localFederationManager.saveFederationRepository(any()) } just runs

        // When
        val result = federationRepositoryService.createFederationRepository(request)

        // Then
        assertNotNull(result)
        verify { localFederationManager.saveFederationRepository(any()) }
        verify {
            federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `test createFederationRepository - cluster not found`() {
        // Given
        val request = createTestFederatedRepositoryCreateRequest(
            federatedClusters = listOf(mockFederatedCluster)
        )

        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns null
        every { localFederationManager.isFederationNameExists(TEST_FEDERATION_NAME) } returns false

        // When & Then
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.createFederationRepository(request)
        }
        assertEquals(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, exception.messageCode)
    }

    @Test
    fun `test createFederationRepository - empty federated clusters`() {
        // Given
        val request = createTestFederatedRepositoryCreateRequest(
            federatedClusters = emptyList()
        )

        // When & Then
        assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.createFederationRepository(request)
        }
    }

    @Test
    fun `test createFederationRepository - federation name exists`() {
        // Given
        val request = createTestFederatedRepositoryCreateRequest(
            federatedClusters = listOf(mockFederatedCluster)
        )

        every { localFederationManager.isFederationNameExists(TEST_FEDERATION_NAME) } returns true

        // When & Then
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.createFederationRepository(request)
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_NAME_EXISTS, exception.messageCode)
    }

    @Test
    fun `test saveFederationRepositoryConfig - success`() {
        // Given
        val request = FederatedRepositoryConfigRequest(
            name = TEST_FEDERATION_NAME,
            federationId = TEST_FEDERATION_ID,
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            selfCluster = mockClusterNodeInfo,
            federatedClusters = emptyList()
        )

        every { localFederationManager.saveFederationRepositoryConfig(request) } returns true

        // When
        val result = federationRepositoryService.saveFederationRepositoryConfig(request)

        // Then
        assertTrue(result)
        verify { localFederationManager.saveFederationRepositoryConfig(request) }
    }

    @Test
    fun `test listFederationRepository - success`() {
        // Given
        val expectedList = listOf(
            FederatedRepositoryInfo(
                name = TEST_FEDERATION_NAME,
                federationId = TEST_FEDERATION_ID,
                projectId = TEST_PROJECT_ID,
                repoName = TEST_REPO_NAME,
                clusterId = TEST_CLUSTER_ID_1,
                federatedClusters = listOf(mockFederatedCluster),
                isFullSyncing = false,
                lastFullSyncStartTime = null,
                lastFullSyncEndTime = null
            )
        )

        every {
            localFederationManager.listFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns expectedList

        // When
        val result = federationRepositoryService.listFederationRepository(
            TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
        )

        // Then
        assertEquals(expectedList, result)
        verify {
            localFederationManager.listFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        }
    }

    @Test
    fun `test deleteFederationRepositoryConfig - success`() {
        // Given
        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every {
            remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, mockFederatedCluster)
        } just runs
        every { federationTaskManager.deleteFederationTasks(listOf(mockFederatedCluster)) } just runs
        every {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } just runs

        // When
        federationRepositoryService.deleteFederationRepositoryConfig(
            TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, true
        )

        // Then
        // 验证对每个联邦集群都调用了deleteRemoteFederationConfig
        verify(exactly = 1) {
            remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, mockFederatedCluster)
        }
        verify { federationTaskManager.deleteFederationTasks(listOf(mockFederatedCluster)) }
        verify {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        }
    }

    @Test
    fun `test removeClusterFromFederation - success with multiple clusters`() {
        // Given
        val remoteClusterName = "remote-cluster"
        val remoteClusterId = TEST_CLUSTER_ID_1
        val anotherCluster = FederatedCluster(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = "another-cluster-id",
            enabled = true
        )
        val federationWithMultipleClusters = createTestTFederatedRepository(
            clusterId = TEST_CLUSTER_ID_1,
            federatedClusters = listOf(mockFederatedCluster, anotherCluster)
        )

        every { localFederationManager.getClusterIdByName(remoteClusterName) } returns remoteClusterId
        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns federationWithMultipleClusters
        every { remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, any()) } just runs
        every {
            remoteFederationManager.deleteRemoteConfigForTargetCluster(TEST_FEDERATION_ID, any(), any())
        } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every {
            localFederationManager.updateFederationClusters(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, any())
        } just runs

        // When
        federationRepositoryService.removeClusterFromFederation(
            TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, remoteClusterName, true
        )

        // Then
        verify { remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, any()) }
        verify {
            remoteFederationManager.deleteRemoteConfigForTargetCluster(TEST_FEDERATION_ID, any(), any())
        }
        verify { federationTaskManager.deleteFederationTasks(any()) }
        verify {
            localFederationManager.updateFederationClusters(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, any())
        }
    }

    @Test
    fun `test removeClusterFromFederation - delete entire federation when only one cluster left`() {
        // Given
        val remoteClusterName = "remote-cluster"
        val remoteClusterId = TEST_CLUSTER_ID_1
        val federationWithOneCluster = createTestTFederatedRepository(
            clusterId = TEST_CLUSTER_ID_1,
            federatedClusters = listOf(createTestFederatedCluster(clusterId = remoteClusterId))
        )

        every { localFederationManager.getClusterIdByName(remoteClusterName) } returns remoteClusterId
        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns federationWithOneCluster
        every { remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, any()) } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } just runs

        // When
        federationRepositoryService.removeClusterFromFederation(
            TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, remoteClusterName, true
        )

        // Then
        verify { remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, any()) }
        verify { federationTaskManager.deleteFederationTasks(any()) }
        verify {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        }
    }

    @Test
    fun `test getCurrentClusterName - success`() {
        // Given
        val expectedClusterName = "test-cluster"
        every {
            localFederationManager.getCurrentClusterName(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns expectedClusterName

        // When
        val result = federationRepositoryService.getCurrentClusterName(
            TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
        )

        // Then
        assertEquals(expectedClusterName, result)
        verify {
            localFederationManager.getCurrentClusterName(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        }
    }

    @Test
    fun `test fullSyncFederationRepository - success`() {
        // Given
        every {
            federationSyncManager.executeFullSync(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } just runs

        // When
        federationRepositoryService.fullSyncFederationRepository(
            TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
        )

        // Then
        verify {
            federationSyncManager.executeFullSync(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        }
    }

    @Test
    fun `test updateFederationRepository - success with cluster changes`() {
        // Given
        val newCluster = FederatedCluster(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = "new-cluster-id",
            enabled = true
        )
        val request = FederatedRepositoryUpdateRequest(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            federationId = TEST_FEDERATION_ID,
            federatedClusters = listOf(newCluster)
        )

        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns mockClusterNodeInfo
        every { clusterNodeService.getByClusterId("new-cluster-id") } returns mockClusterNodeInfo.copy(
            id = "new-cluster-id",
            name = "new-cluster"
        )
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(
            "new-cluster-id" to mockClusterNodeInfo.copy(id = "new-cluster-id", name = "new-cluster")
        )
        every {
            remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any())
        } returns "new-task-id"
        every { remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, any()) } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every { localFederationManager.getClusterIdByName("test-cluster") } returns TEST_CLUSTER_ID_1
        every {
            remoteFederationManager.deleteRemoteConfigForTargetCluster(TEST_FEDERATION_ID, any(), any())
        } just runs
        every {
            localFederationManager.updateFederationClusters(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, any())
        } just runs
        every { localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID) } just runs
        // When
        val result = federationRepositoryService.updateFederationRepository(request)

        // Then
        assertTrue(result)
        verify {
            localFederationManager.updateFederationClusters(any(), any(), any(), any())
        }
    }

    @Test
    fun `test updateFederationRepository - no changes detected`() {
        // Given
        val request = FederatedRepositoryUpdateRequest(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            federationId = TEST_FEDERATION_ID,
            federatedClusters = listOf(mockFederatedCluster)
        )

        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository

        // When
        val result = federationRepositoryService.updateFederationRepository(request)

        // Then
        assertFalse(result)
    }

    @Test
    fun `test updateFederationRepository - federation not found`() {
        // Given
        val request = FederatedRepositoryUpdateRequest(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            federationId = TEST_FEDERATION_ID,
            federatedClusters = listOf(mockFederatedCluster)
        )

        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns null

        // When & Then
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.updateFederationRepository(request)
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND, exception.messageCode)
    }

    @Test
    fun `test updateFederationRepository - cluster size changes`() {
        // Given - 测试集群数量变化的情况
        val newCluster = FederatedCluster(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = "new-cluster-id",
            enabled = true
        )
        val request = FederatedRepositoryUpdateRequest(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            federationId = TEST_FEDERATION_ID,
            federatedClusters = listOf(mockFederatedCluster, newCluster) // 增加一个集群
        )

        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns mockClusterNodeInfo
        every { clusterNodeService.getByClusterId("new-cluster-id") } returns mockClusterNodeInfo.copy(
            id = "new-cluster-id",
            name = "new-cluster"
        )
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(
            "new-cluster-id" to mockClusterNodeInfo.copy(id = "new-cluster-id", name = "new-cluster")
        )
        every {
            remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any())
        } returns "new-task-id"
        every { localFederationManager.getClusterIdByName("test-cluster") } returns TEST_CLUSTER_ID_1
        every {
            localFederationManager.updateFederationClusters(any(), any(), any(), any())
        } just runs

        // When
        val result = federationRepositoryService.updateFederationRepository(request)

        // Then
        assertTrue(result)
        verify {
            localFederationManager.updateFederationClusters(any(), any(), any(), any())
        }
    }

    @Test
    fun `test updateFederationRepository - cluster ID changes`() {
        // Given - 测试集群ID变化的情况
        val differentCluster = FederatedCluster(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = "different-cluster-id",
            enabled = true
        )
        val request = FederatedRepositoryUpdateRequest(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            federationId = TEST_FEDERATION_ID,
            federatedClusters = listOf(differentCluster)
        )

        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns mockClusterNodeInfo
        every {
            clusterNodeService.getByClusterId("different-cluster-id")
        } returns mockClusterNodeInfo.copy(id = "different-cluster-id", name = "different-cluster")
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(
            "different-cluster-id" to mockClusterNodeInfo.copy(id = "different-cluster-id", name = "different-cluster")
        )
        every {
            remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any())
        } just runs
        every {
            federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any())
        } returns "new-task-id"
        every { remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, any()) } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every { localFederationManager.getClusterIdByName("test-cluster") } returns TEST_CLUSTER_ID_1
        every {
            remoteFederationManager.deleteRemoteConfigForTargetCluster(TEST_FEDERATION_ID, any(), any())
        } just runs

        every {
            localFederationManager.updateFederationClusters(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, any())
        } just runs
        every { localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID) } just runs

        // When
        val result = federationRepositoryService.updateFederationRepository(request)

        // Then
        assertTrue(result)
        verify {
            localFederationManager.updateFederationClusters(any(), any(), any(), any())
        }
    }

    @Test
    fun `test createFederationRepository - exception handling`() {
        // Given
        val request = FederatedRepositoryCreateRequest(
            name = TEST_FEDERATION_NAME,
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = TEST_CLUSTER_ID_1,
            federatedClusters = listOf(mockFederatedCluster)
        )

        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns mockClusterNodeInfo
        every { localFederationManager.isFederationNameExists(TEST_FEDERATION_NAME) } returns false
        every { federationTaskManager.getClusterInfoMap(any()) } throws RuntimeException("Test exception")

        // When & Then
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.createFederationRepository(request)
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR, exception.messageCode)
    }

    @Test
    fun `test deleteFederationRepositoryConfig - exception handling`() {
        // Given
        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every { remoteFederationManager.deleteRemoteFederationConfig(any(), any()) } just runs
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } throws RuntimeException("Database connection failed")

        // When & Then
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.deleteFederationRepositoryConfig(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, true
            )
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_DELETE_ERROR, exception.messageCode)
    }

    @Test
    fun `test deleteFederationRepositoryConfig - remote deletion failure should throw exception`() {
        // Given
        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every {
            remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, mockFederatedCluster)
        } throws RuntimeException("Remote cluster unreachable")
        every { federationTaskManager.deleteFederationTasks(any()) } just runs
        every {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } just runs

        // When & Then - 远程删除失败会导致整个删除操作失败
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.deleteFederationRepositoryConfig(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, true
            )
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_DELETE_ERROR, exception.messageCode)
    }

    @Test
    fun `test deleteFederationRepositoryConfig - task deletion failure should throw exception`() {
        // Given
        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every {
            remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, mockFederatedCluster)
        } just runs
        every {
            federationTaskManager.deleteFederationTasks(listOf(mockFederatedCluster))
        } throws RuntimeException("Task manager error")
        every {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } just runs

        // When & Then - 任务删除失败会导致整个删除操作失败
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.deleteFederationRepositoryConfig(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, true
            )
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_DELETE_ERROR, exception.messageCode)
    }

    @Test
    fun `test deleteFederationRepositoryConfig - local config deletion failure should throw exception`() {
        // Given
        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns mockTFederatedRepository
        every {
            remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, mockFederatedCluster)
        } just runs
        every { federationTaskManager.deleteFederationTasks(listOf(mockFederatedCluster)) } just runs
        every {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } throws RuntimeException("Critical database error")

        // When & Then - 本地配置删除失败应该抛出异常
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.deleteFederationRepositoryConfig(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, true
            )
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_DELETE_ERROR, exception.messageCode)
    }

    @Test
    fun `test deleteFederationRepositoryConfig - partial remote deletion failure should throw exception`() {
        // Given - 模拟多个集群，其中一个远程删除失败
        val anotherCluster = FederatedCluster(
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = "another-cluster-id",
            enabled = true
        )
        val federationWithMultipleClusters = createTestTFederatedRepository(
            clusterId = TEST_CLUSTER_ID_1,
            federatedClusters = listOf(mockFederatedCluster, anotherCluster)
        )

        every {
            localFederationManager.getFederationRepository(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID
            )
        } returns federationWithMultipleClusters

        // 第一个集群删除成功，第二个集群删除失败
        every {
            remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, mockFederatedCluster)
        } just runs
        every {
            remoteFederationManager.deleteRemoteFederationConfig(TEST_FEDERATION_ID, anotherCluster)
        } throws RuntimeException("Remote cluster unreachable")

        every { federationTaskManager.deleteFederationTasks(listOf(mockFederatedCluster, anotherCluster)) } just runs
        every {
            localFederationManager.deleteConfig(TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID)
        } just runs

        // When & Then - 部分远程删除失败会导致整个删除操作失败
        val exception = assertThrows(ErrorCodeException::class.java) {
            federationRepositoryService.deleteFederationRepositoryConfig(
                TEST_PROJECT_ID, TEST_REPO_NAME, TEST_FEDERATION_ID, true
            )
        }
        assertEquals(ReplicationMessageCode.FEDERATION_REPOSITORY_DELETE_ERROR, exception.messageCode)
    }
}