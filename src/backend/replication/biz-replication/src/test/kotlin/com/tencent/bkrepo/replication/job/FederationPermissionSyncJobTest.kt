package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.model.TFederationGroup
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeStatus
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.replica.replicator.standalone.FederationReplicator
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationGroupService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class FederationPermissionSyncJobTest {

    @MockK
    private lateinit var federatedRepositoryDao: FederatedRepositoryDao

    @MockK
    private lateinit var clusterNodeService: ClusterNodeService

    @MockK
    private lateinit var federationReplicator: FederationReplicator

    @MockK
    private lateinit var federationGroupService: FederationGroupService

    @MockK
    private lateinit var mockReplicaClient: ArtifactReplicaClient

    private lateinit var job: FederationPermissionSyncJob

    @BeforeEach
    fun setUp() {
        mockkObject(FeignClientFactory)
        every {
            FeignClientFactory.create(
                eq(ArtifactReplicaClient::class.java), any<ClusterInfo>(),
                anyNullable<String>(), anyNullable<String>(), any<Boolean>()
            )
        } returns mockReplicaClient

        job = FederationPermissionSyncJob(
            federatedRepositoryDao = federatedRepositoryDao,
            clusterNodeService = clusterNodeService,
            federationReplicator = federationReplicator,
            federationGroupService = federationGroupService
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(FeignClientFactory)
    }

    // ==================== early-exit guards ====================

    @Test
    fun `sync - no federation groups should skip everything`() {
        every { federationGroupService.listAll() } returns emptyList()

        job.sync()

        verify(exactly = 0) { federatedRepositoryDao.findAll() }
        verify(exactly = 0) { federationReplicator.replicaUsersTo(any(), any()) }
    }

    @Test
    fun `sync - federation repositories query failure should return early`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } throws RuntimeException("mongo down")

        job.sync()

        verify(exactly = 0) { clusterNodeService.getByClusterId(any()) }
    }

    @Test
    fun `sync - no federation repositories should skip sync`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns emptyList()

        job.sync()

        verify(exactly = 0) { clusterNodeService.getByClusterId(any()) }
    }

    @Test
    fun `sync - cluster not found should skip that cluster`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(
            buildRepo("proj-1", listOf("ghost-cluster"))
        )
        every { clusterNodeService.getByClusterId("ghost-cluster") } returns null

        job.sync()

        verify(exactly = 0) { federationReplicator.replicaUsersTo(any(), any()) }
    }

    // ==================== deduplication ====================

    @Test
    fun `sync - global data should be synced only once per clusterId across multiple repos`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(
            buildRepo("proj-1", listOf("cluster-a")),
            buildRepo("proj-2", listOf("cluster-a"))
        )
        every { clusterNodeService.getByClusterId("cluster-a") } returns buildClusterNodeInfo("cluster-a")
        stubAllReplicatorMethods()

        job.sync()

        // cluster-a appears in two repos but global sync should happen only once
        verify(exactly = 1) { federationReplicator.replicaUsersTo(mockReplicaClient, "cluster-a") }
        verify(exactly = 1) { federationReplicator.replicaAccountsTo(mockReplicaClient, "cluster-a") }
    }

    @Test
    fun `sync - project data should be synced only once per projectId-clusterId pair`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(
            buildRepo("proj-1", listOf("cluster-a"), repoName = "repo-x"),
            buildRepo("proj-1", listOf("cluster-a"), repoName = "repo-y")
        )
        every { clusterNodeService.getByClusterId("cluster-a") } returns buildClusterNodeInfo("cluster-a")
        stubAllReplicatorMethods()

        job.sync()

        // proj-1|cluster-a appears in two repos but project sync should happen only once
        verify(exactly = 1) { federationReplicator.replicaRolesTo(mockReplicaClient, "proj-1", "cluster-a") }
        verify(exactly = 1) { federationReplicator.replicaPermissionsTo(mockReplicaClient, "proj-1", "cluster-a") }
    }

    @Test
    fun `sync - different projects with same cluster should each get project sync`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(
            buildRepo("proj-1", listOf("cluster-a")),
            buildRepo("proj-2", listOf("cluster-a"))
        )
        every { clusterNodeService.getByClusterId("cluster-a") } returns buildClusterNodeInfo("cluster-a")
        stubAllReplicatorMethods()

        job.sync()

        verify(exactly = 1) { federationReplicator.replicaRolesTo(mockReplicaClient, "proj-1", "cluster-a") }
        verify(exactly = 1) { federationReplicator.replicaRolesTo(mockReplicaClient, "proj-2", "cluster-a") }
    }

    // ==================== sync method order ====================

    @Test
    fun `syncGlobalData - should sync in order accounts then oauthTokens then users then keys then externalPermissions`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(buildRepo("proj-1", listOf("cluster-a")))
        every { clusterNodeService.getByClusterId("cluster-a") } returns buildClusterNodeInfo("cluster-a")
        stubAllReplicatorMethods()

        job.sync()

        verifyOrder {
            federationReplicator.replicaAccountsTo(mockReplicaClient, "cluster-a")
            federationReplicator.replicaOauthTokensTo(mockReplicaClient, "cluster-a")
            federationReplicator.replicaUsersTo(mockReplicaClient, "cluster-a")
            federationReplicator.replicaKeysTo(mockReplicaClient, "cluster-a")
            federationReplicator.replicaExternalPermissionsTo(mockReplicaClient, "cluster-a")
        }
    }

    @Test
    fun `syncProjectData - should sync in order roles then permissions then tokens then paths then proxies then repoAuthConfig`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(buildRepo("proj-1", listOf("cluster-a")))
        every { clusterNodeService.getByClusterId("cluster-a") } returns buildClusterNodeInfo("cluster-a")
        stubAllReplicatorMethods()

        job.sync()

        verifyOrder {
            federationReplicator.replicaRolesTo(mockReplicaClient, "proj-1", "cluster-a")
            federationReplicator.replicaPermissionsTo(mockReplicaClient, "proj-1", "cluster-a")
            federationReplicator.replicaTemporaryTokensTo(mockReplicaClient, "proj-1", "cluster-a")
            federationReplicator.replicaPersonalPathsTo(mockReplicaClient, "proj-1", "cluster-a")
            federationReplicator.replicaProxiesTo(mockReplicaClient, "proj-1", "cluster-a")
            federationReplicator.replicaRepoAuthConfigTo(mockReplicaClient, "proj-1", "cluster-a")
        }
    }

    // ==================== error resilience ====================

    @Test
    fun `sync - global data sync failure should be swallowed and project sync continues`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(buildRepo("proj-1", listOf("cluster-a")))
        every { clusterNodeService.getByClusterId("cluster-a") } returns buildClusterNodeInfo("cluster-a")
        every { federationReplicator.replicaAccountsTo(any(), any()) } throws RuntimeException("accounts failed")
        // remaining global methods throw too, simulating full global failure
        every { federationReplicator.replicaOauthTokensTo(any(), any()) } throws RuntimeException("oauth failed")
        every { federationReplicator.replicaUsersTo(any(), any()) } throws RuntimeException("users failed")
        every { federationReplicator.replicaKeysTo(any(), any()) } throws RuntimeException("keys failed")
        every { federationReplicator.replicaExternalPermissionsTo(any(), any()) } throws RuntimeException("ext perms failed")
        stubProjectReplicatorMethods()

        job.sync()

        // project sync must still proceed despite global failure
        verify(exactly = 1) { federationReplicator.replicaRolesTo(mockReplicaClient, "proj-1", "cluster-a") }
    }

    @Test
    fun `sync - project data sync failure should be swallowed and next project continues`() {
        every { federationGroupService.listAll() } returns listOf(buildGroup())
        every { federatedRepositoryDao.findAll() } returns listOf(
            buildRepo("proj-fail", listOf("cluster-a")),
            buildRepo("proj-ok", listOf("cluster-b"))
        )
        every { clusterNodeService.getByClusterId("cluster-a") } returns buildClusterNodeInfo("cluster-a")
        every { clusterNodeService.getByClusterId("cluster-b") } returns buildClusterNodeInfo("cluster-b")
        stubGlobalReplicatorMethods()
        every { federationReplicator.replicaRolesTo(any(), "proj-fail", any()) } throws RuntimeException("roles failed")
        every { federationReplicator.replicaPermissionsTo(any(), "proj-fail", any()) } throws RuntimeException("perms failed")
        every { federationReplicator.replicaTemporaryTokensTo(any(), "proj-fail", any()) } throws RuntimeException()
        every { federationReplicator.replicaPersonalPathsTo(any(), "proj-fail", any()) } throws RuntimeException()
        every { federationReplicator.replicaProxiesTo(any(), "proj-fail", any()) } throws RuntimeException()
        every { federationReplicator.replicaRepoAuthConfigTo(any(), "proj-fail", any()) } throws RuntimeException()
        stubProjectReplicatorMethodsForProject("proj-ok")

        job.sync()

        verify(exactly = 1) { federationReplicator.replicaRolesTo(mockReplicaClient, "proj-ok", "cluster-b") }
    }

    // ==================== helper functions ====================

    private fun stubAllReplicatorMethods() {
        stubGlobalReplicatorMethods()
        every { federationReplicator.replicaRolesTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaPermissionsTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaTemporaryTokensTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaPersonalPathsTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaProxiesTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaRepoAuthConfigTo(any(), any(), any()) } returns Unit
    }

    private fun stubGlobalReplicatorMethods() {
        every { federationReplicator.replicaAccountsTo(any(), any()) } returns Unit
        every { federationReplicator.replicaOauthTokensTo(any(), any()) } returns Unit
        every { federationReplicator.replicaUsersTo(any(), any()) } returns Unit
        every { federationReplicator.replicaKeysTo(any(), any()) } returns Unit
        every { federationReplicator.replicaExternalPermissionsTo(any(), any()) } returns Unit
    }

    private fun stubProjectReplicatorMethods() {
        every { federationReplicator.replicaRolesTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaPermissionsTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaTemporaryTokensTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaPersonalPathsTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaProxiesTo(any(), any(), any()) } returns Unit
        every { federationReplicator.replicaRepoAuthConfigTo(any(), any(), any()) } returns Unit
    }

    private fun stubProjectReplicatorMethodsForProject(projectId: String) {
        every { federationReplicator.replicaRolesTo(any(), projectId, any()) } returns Unit
        every { federationReplicator.replicaPermissionsTo(any(), projectId, any()) } returns Unit
        every { federationReplicator.replicaTemporaryTokensTo(any(), projectId, any()) } returns Unit
        every { federationReplicator.replicaPersonalPathsTo(any(), projectId, any()) } returns Unit
        every { federationReplicator.replicaProxiesTo(any(), projectId, any()) } returns Unit
        every { federationReplicator.replicaRepoAuthConfigTo(any(), projectId, any()) } returns Unit
    }

    private fun buildGroup() = TFederationGroup(
        id = "group-1",
        name = "test-group",
        currentClusterId = "local",
        clusterIds = listOf("local"),
        createdBy = "admin",
        lastModifiedBy = "admin"
    )

    private fun buildRepo(
        projectId: String,
        clusterIds: List<String>,
        repoName: String = "test-repo"
    ) = TFederatedRepository(
        id = "repo-$projectId",
        projectId = projectId,
        repoName = repoName,
        clusterId = "local",
        federationId = "fed-1",
        name = "fed-$projectId",
        federatedClusters = clusterIds.map { FederatedCluster(projectId = projectId, repoName = repoName, clusterId = it) },
        createdBy = "admin",
        createdDate = LocalDateTime.now(),
        lastModifiedBy = "admin",
        lastModifiedDate = LocalDateTime.now()
    )

    private fun buildClusterNodeInfo(id: String, name: String = id) = ClusterNodeInfo(
        id = id,
        name = name,
        status = ClusterNodeStatus.HEALTHY,
        errorReason = null,
        type = ClusterNodeType.STANDALONE,
        url = "http://$name:8080",
        username = "user",
        password = "pass",
        certificate = null,
        createdBy = "admin",
        createdDate = "2024-01-01",
        lastModifiedBy = "admin",
        lastModifiedDate = "2024-01-01",
        detectType = DetectType.PING,
        lastReportTime = null
    )
}
