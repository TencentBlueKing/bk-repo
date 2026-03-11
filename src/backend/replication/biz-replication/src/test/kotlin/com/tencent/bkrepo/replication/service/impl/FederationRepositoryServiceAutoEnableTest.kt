package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.impl.federation.FederationSyncManager
import com.tencent.bkrepo.replication.service.impl.federation.FederationTaskManager
import com.tencent.bkrepo.replication.service.impl.federation.LocalFederationManager
import com.tencent.bkrepo.replication.service.impl.federation.RemoteFederationManager
import com.tencent.bkrepo.replication.manager.FederationDiffManager
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

/**
 * 测试 autoEnableFederation 的正常流程和幂等性
 */
@ExtendWith(MockKExtension::class)
class FederationRepositoryServiceAutoEnableTest : FederationRepositoryServiceTestBase() {

    @MockK private lateinit var localFederationManager: LocalFederationManager
    @MockK private lateinit var remoteFederationManager: RemoteFederationManager
    @MockK private lateinit var federationTaskManager: FederationTaskManager
    @MockK private lateinit var federationSyncManager: FederationSyncManager
    @MockK private lateinit var clusterNodeService: ClusterNodeService
    @MockK private lateinit var federationDiffManager: FederationDiffManager

    private lateinit var service: FederationRepositoryServiceImpl

    private val centerCluster = ClusterNodeInfo(
        id = "center-id",
        name = "center",
        url = "http://center.com",
        username = "admin",
        password = "pw",
        certificate = null,
        accessKey = null,
        secretKey = null,
        appId = null,
        createdBy = "system",
        createdDate = LocalDateTime.now().toString(),
        lastModifiedBy = "system",
        lastModifiedDate = LocalDateTime.now().toString(),
        detectType = null,
        lastReportTime = null,
        status = ClusterNodeStatus.HEALTHY,
        errorReason = null,
        type = ClusterNodeType.STANDALONE
    )

    private val remoteCluster = centerCluster.copy(id = TEST_CLUSTER_ID_1, name = "remote-1", url = "http://remote1.com")

    @BeforeEach
    fun setUp() {
        service = FederationRepositoryServiceImpl(
            localFederationManager,
            remoteFederationManager,
            federationTaskManager,
            federationSyncManager,
            clusterNodeService,
            federationDiffManager
        )
    }

    // ==================== 幂等性：已存在同名任务时跳过 ====================

    @Test
    fun `autoEnableFederation - already enabled for this group should be idempotent`() {
        val groupId = "group-abc"
        val autoTaskName = "$TEST_PROJECT_ID-$TEST_REPO_NAME-auto-$groupId"
        val existingInfo = FederatedRepositoryInfo(
            name = autoTaskName,
            federationId = "fed-1",
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = "center-id",
            federatedClusters = emptyList(),
            isFullSyncing = false,
            lastFullSyncStartTime = null,
            lastFullSyncEndTime = null
        )
        every {
            localFederationManager.listFederationRepository(TEST_PROJECT_ID, TEST_REPO_NAME, null)
        } returns listOf(existingInfo)

        service.autoEnableFederation(TEST_PROJECT_ID, TEST_REPO_NAME, groupId, "center-id", listOf("center-id", TEST_CLUSTER_ID_1))

        verify(exactly = 0) { clusterNodeService.getByClusterId(any()) }
        verify(exactly = 0) { localFederationManager.saveFederationRepository(any()) }
    }

    @Test
    fun `autoEnableFederation - different group on same repo should NOT be skipped`() {
        val groupId = "group-new"
        val existingInfo = FederatedRepositoryInfo(
            name = "$TEST_PROJECT_ID-$TEST_REPO_NAME-auto-group-old",
            federationId = "fed-1",
            projectId = TEST_PROJECT_ID,
            repoName = TEST_REPO_NAME,
            clusterId = "center-id",
            federatedClusters = emptyList(),
            isFullSyncing = false,
            lastFullSyncStartTime = null,
            lastFullSyncEndTime = null
        )
        every {
            localFederationManager.listFederationRepository(TEST_PROJECT_ID, TEST_REPO_NAME, null)
        } returns listOf(existingInfo)
        every { clusterNodeService.getByClusterId("center-id") } returns centerCluster
        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns remoteCluster
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(TEST_CLUSTER_ID_1 to remoteCluster)
        every { remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any()) } just runs
        every { remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any()) } just runs
        every { federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any()) } returns "task-1"
        every { localFederationManager.saveFederationRepository(any()) } just runs
        every { localFederationManager.isFederationNameExists(any()) } returns false

        service.autoEnableFederation(TEST_PROJECT_ID, TEST_REPO_NAME, groupId, "center-id", listOf("center-id", TEST_CLUSTER_ID_1))

        verify(exactly = 1) { localFederationManager.saveFederationRepository(any()) }
    }

    // ==================== 正常流程 ====================

    @Test
    fun `autoEnableFederation - no existing federation should create with mirror naming`() {
        val groupId = "group-xyz"
        every {
            localFederationManager.listFederationRepository(TEST_PROJECT_ID, TEST_REPO_NAME, null)
        } returns emptyList()
        every { clusterNodeService.getByClusterId("center-id") } returns centerCluster
        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns remoteCluster
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(TEST_CLUSTER_ID_1 to remoteCluster)
        every { remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any()) } just runs
        every { remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any()) } just runs
        every { federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any()) } returns "task-new"
        val savedSlot = slot<com.tencent.bkrepo.replication.model.TFederatedRepository>()
        every { localFederationManager.saveFederationRepository(capture(savedSlot)) } just runs
        every { localFederationManager.isFederationNameExists(any()) } returns false

        service.autoEnableFederation(TEST_PROJECT_ID, TEST_REPO_NAME, groupId, "center-id", listOf("center-id", TEST_CLUSTER_ID_1))

        verify(exactly = 1) { localFederationManager.saveFederationRepository(any()) }
        assertEquals(TEST_PROJECT_ID, savedSlot.captured.projectId)
        assertEquals(TEST_REPO_NAME, savedSlot.captured.repoName)
        assertEquals("$TEST_PROJECT_ID-$TEST_REPO_NAME-auto-$groupId", savedSlot.captured.name)
    }

    @Test
    fun `autoEnableFederation - center cluster id filtered from federated targets`() {
        val groupId = "group-filter"
        // clusterIds 包含 center 自身，应被过滤掉，只有 TEST_CLUSTER_ID_1 作为对端
        every {
            localFederationManager.listFederationRepository(TEST_PROJECT_ID, TEST_REPO_NAME, null)
        } returns emptyList()
        every { clusterNodeService.getByClusterId("center-id") } returns centerCluster
        every { clusterNodeService.getByClusterId(TEST_CLUSTER_ID_1) } returns remoteCluster
        every { federationTaskManager.getClusterInfoMap(any()) } returns mapOf(TEST_CLUSTER_ID_1 to remoteCluster)
        every { remoteFederationManager.createRemoteProjectAndRepo(any(), any(), any(), any(), any(), any()) } just runs
        every { remoteFederationManager.syncFederationConfig(any(), any(), any(), any(), any(), any()) } just runs
        every { federationTaskManager.createOrUpdateTask(any(), any(), any(), any(), any(), any()) } returns "task-f"
        val savedSlot = slot<com.tencent.bkrepo.replication.model.TFederatedRepository>()
        every { localFederationManager.saveFederationRepository(capture(savedSlot)) } just runs
        every { localFederationManager.isFederationNameExists(any()) } returns false

        // center-id 在 clusterIds 中应被过滤
        service.autoEnableFederation(
            TEST_PROJECT_ID, TEST_REPO_NAME, groupId, "center-id",
            listOf("center-id", TEST_CLUSTER_ID_1)
        )

        val federatedClusters = savedSlot.captured.federatedClusters
        assert(federatedClusters.none { it.clusterId == "center-id" }) {
            "center cluster should be filtered out from federated targets"
        }
    }

    @Test
    fun `autoEnableFederation - all valid clusterIds belong to center should skip creation`() {
        val groupId = "group-skip"
        every {
            localFederationManager.listFederationRepository(TEST_PROJECT_ID, TEST_REPO_NAME, null)
        } returns emptyList()
        every { clusterNodeService.getByClusterId("center-id") } returns centerCluster

        // 传入的 clusterIds 只有 center 自身，过滤后为空，应跳过
        service.autoEnableFederation(TEST_PROJECT_ID, TEST_REPO_NAME, groupId, "center-id", listOf("center-id"))

        verify(exactly = 0) { localFederationManager.saveFederationRepository(any()) }
    }

    @Test
    fun `autoEnableFederation - unknown clusterId should be ignored`() {
        val groupId = "group-unknown"
        every {
            localFederationManager.listFederationRepository(TEST_PROJECT_ID, TEST_REPO_NAME, null)
        } returns emptyList()
        every { clusterNodeService.getByClusterId("center-id") } returns centerCluster
        every { clusterNodeService.getByClusterId("unknown-id") } returns null

        // getByClusterId 返回 null 时，该集群被忽略，最终 federatedClusters 为空，跳过创建
        service.autoEnableFederation(TEST_PROJECT_ID, TEST_REPO_NAME, groupId, "center-id", listOf("center-id", "unknown-id"))

        verify(exactly = 0) { localFederationManager.saveFederationRepository(any()) }
    }
}
