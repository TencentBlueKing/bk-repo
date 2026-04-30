package com.tencent.bkrepo.replication.replica.type.federation

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
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
import com.tencent.bkrepo.replication.replica.executor.FederationThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.replicator.standalone.FederationReplicator
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationGroupService
import com.tencent.bkrepo.replication.util.FederationDataBuilder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.messaging.Message
import java.time.LocalDateTime
import java.util.concurrent.ThreadPoolExecutor

@ExtendWith(MockKExtension::class)
class FederationPermissionEventConsumerTest {

    @MockK
    private lateinit var federationGroupService: FederationGroupService

    @MockK
    private lateinit var federatedRepositoryDao: FederatedRepositoryDao

    @MockK
    private lateinit var clusterNodeService: ClusterNodeService

    @MockK
    private lateinit var federationReplicator: FederationReplicator

    @MockK
    private lateinit var mockReplicaClient: ArtifactReplicaClient

    private lateinit var consumer: FederationPermissionEventConsumer

    @BeforeEach
    fun setUp() {
        mockkObject(FederationThreadPoolExecutor)
        val syncExecutor = mockk<ThreadPoolExecutor>()
        every { syncExecutor.execute(any()) } answers { firstArg<Runnable>().run() }
        every { FederationThreadPoolExecutor.instance } returns syncExecutor

        mockkObject(FeignClientFactory)
        mockkObject(FederationDataBuilder)
        every { FederationDataBuilder.buildClusterInfo(any()) } returns mockk(relaxed = true)
        every {
            FeignClientFactory.create(
                eq(ArtifactReplicaClient::class.java), any<ClusterInfo>(),
                anyNullable<String>(), anyNullable<String>(), any<Boolean>()
            )
        } returns mockReplicaClient

        consumer = FederationPermissionEventConsumer(
            federationGroupService = federationGroupService,
            federatedRepositoryDao = federatedRepositoryDao,
            clusterNodeService = clusterNodeService,
            federationReplicator = federationReplicator
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(FederationThreadPoolExecutor)
        unmockkObject(FeignClientFactory)
        unmockkObject(FederationDataBuilder)
    }

    // ==================== resolveTargetClusterIds - cache behavior ====================

    @Test
    fun `federation group cache - multiple events should only call listAll once within TTL`() {
        val group = buildFederationGroup(currentClusterId = "local", clusterIds = listOf("local", "remote-1"))
        every { federationGroupService.listAll() } returns listOf(group)
        every { clusterNodeService.getByClusterId(any()) } returns null

        consumer.action(buildMessage(EventType.USER_CREATED, "user-a", "proj"))
        consumer.action(buildMessage(EventType.USER_UPDATED, "user-b", "proj"))

        // listAll should only be called once due to caching
        verify(exactly = 1) { federationGroupService.listAll() }
    }

    // ==================== resolveTargetClusterIds - GLOBAL_TYPES ====================

    @Test
    fun `GLOBAL event - should collect remote cluster IDs from all federation groups`() {
        val group = buildFederationGroup(
            currentClusterId = "local", clusterIds = listOf("local", "remote-1", "remote-2")
        )
        every { federationGroupService.listAll() } returns listOf(group)
        every { clusterNodeService.getByClusterId(any()) } returns null

        consumer.action(buildMessage(EventType.USER_CREATED, "user-a", "project-x"))

        verify(exactly = 1) { clusterNodeService.getByClusterId("remote-1") }
        verify(exactly = 1) { clusterNodeService.getByClusterId("remote-2") }
        verify(exactly = 0) { clusterNodeService.getByClusterId("local") }
    }

    @Test
    fun `GLOBAL event - multiple groups should deduplicate cluster IDs`() {
        val group1 = buildFederationGroup("g1", "local", listOf("local", "remote-1"))
        val group2 = buildFederationGroup("g2", "local", listOf("local", "remote-1", "remote-2"))
        every { federationGroupService.listAll() } returns listOf(group1, group2)
        every { clusterNodeService.getByClusterId(any()) } returns null

        consumer.action(buildMessage(EventType.USER_UPDATED, "user-b", "project-x"))

        // remote-1 appears in both groups but should only be looked up once
        verify(exactly = 1) { clusterNodeService.getByClusterId("remote-1") }
        verify(exactly = 1) { clusterNodeService.getByClusterId("remote-2") }
    }

    @Test
    fun `GLOBAL event - no federation groups should skip dispatch`() {
        every { federationGroupService.listAll() } returns emptyList()

        consumer.action(buildMessage(EventType.USER_CREATED, "user-a", "project-x"))

        verify(exactly = 0) { clusterNodeService.getByClusterId(any()) }
    }

    // ==================== resolveTargetClusterIds - PROJECT_TYPES ====================

    @Test
    fun `PROJECT event - should collect federated cluster IDs for the project`() {
        val group = buildFederationGroup(
            currentClusterId = "local", clusterIds = listOf("local", "cluster-a", "cluster-b")
        )
        every { federationGroupService.listAll() } returns listOf(group)
        val repo = buildFederatedRepository("proj-1", listOf("cluster-a", "cluster-b"))
        every { federatedRepositoryDao.findByProjectId("proj-1") } returns listOf(repo)
        every { clusterNodeService.getByClusterId(any()) } returns null

        consumer.action(buildMessage(EventType.ROLE_CREATED, "role-1", "proj-1"))

        verify(exactly = 1) { clusterNodeService.getByClusterId("cluster-a") }
        verify(exactly = 1) { clusterNodeService.getByClusterId("cluster-b") }
    }

    @Test
    fun `PROJECT event - no federated repos for project should skip dispatch`() {
        val group = buildFederationGroup(currentClusterId = "local", clusterIds = listOf("local", "remote-1"))
        every { federationGroupService.listAll() } returns listOf(group)
        every { federatedRepositoryDao.findByProjectId("proj-empty") } returns emptyList()

        consumer.action(buildMessage(EventType.PERMISSION_CREATED, "perm-1", "proj-empty"))

        verify(exactly = 0) { clusterNodeService.getByClusterId(any()) }
    }

    // ==================== action - error handling ====================

    @Test
    fun `action - cluster not found should skip cluster and continue`() {
        val group = buildFederationGroup(clusterIds = listOf("local", "ghost-cluster", "valid-cluster"))
        every { federationGroupService.listAll() } returns listOf(group)
        every { clusterNodeService.getByClusterId("ghost-cluster") } returns null
        every { clusterNodeService.getByClusterId("valid-cluster") } returns buildClusterNodeInfo("valid-cluster")
        every { federationReplicator.replicaUserChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.USER_CREATED, "user-a", "proj"))

        verify(exactly = 1) {
            federationReplicator.replicaUserChangeTo(
                any(), "user-a", false, "cluster-valid-cluster"
            )
        }
    }

    @Test
    fun `action - exception in one cluster should not abort remaining clusters`() {
        val group = buildFederationGroup(clusterIds = listOf("local", "fail-cluster", "ok-cluster"))
        every { federationGroupService.listAll() } returns listOf(group)
        every { clusterNodeService.getByClusterId("fail-cluster") } throws RuntimeException("connection refused")
        every { clusterNodeService.getByClusterId("ok-cluster") } returns buildClusterNodeInfo("ok-cluster")
        every { federationReplicator.replicaUserChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.USER_DELETED, "user-b", "proj"))

        verify(exactly = 1) { federationReplicator.replicaUserChangeTo(any(), "user-b", true, "cluster-ok-cluster") }
    }

    // ==================== dispatchToCluster - event routing ====================

    @Test
    fun `USER_CREATED should call replicaUserChangeTo with deleted=false`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaUserChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.USER_CREATED, "alice", "proj"))

        verify { federationReplicator.replicaUserChangeTo(mockReplicaClient, "alice", false, "remote-node") }
    }

    @Test
    fun `USER_UPDATED should call replicaUserChangeTo with deleted=false`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaUserChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.USER_UPDATED, "alice", "proj"))

        verify { federationReplicator.replicaUserChangeTo(mockReplicaClient, "alice", false, "remote-node") }
    }

    @Test
    fun `USER_DELETED should call replicaUserChangeTo with deleted=true`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaUserChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.USER_DELETED, "alice", "proj"))

        verify { federationReplicator.replicaUserChangeTo(mockReplicaClient, "alice", true, "remote-node") }
    }

    @Test
    fun `ROLE_CREATED should call replicaRoleChangeTo with deleted=false`() {
        setupProjectCluster("proj-1", "remote-node")
        every { federationReplicator.replicaRoleChangeTo(any(), any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.ROLE_CREATED, "role-mgr", "proj-1"))

        verify {
            federationReplicator.replicaRoleChangeTo(
                mockReplicaClient, "role-mgr", "proj-1", false, "remote-node"
            )
        }
    }

    @Test
    fun `ROLE_DELETED should call replicaRoleChangeTo with deleted=true`() {
        setupProjectCluster("proj-1", "remote-node")
        every { federationReplicator.replicaRoleChangeTo(any(), any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.ROLE_DELETED, "role-mgr", "proj-1"))

        verify {
            federationReplicator.replicaRoleChangeTo(
                mockReplicaClient, "role-mgr", "proj-1", true, "remote-node"
            )
        }
    }

    @Test
    fun `PERMISSION_CREATED should call replicaPermissionChangeTo with deleted=false`() {
        setupProjectCluster("proj-1", "remote-node")
        every {
            federationReplicator.replicaPermissionChangeTo(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns Unit

        consumer.action(buildMessage(EventType.PERMISSION_CREATED, "perm-id", "proj-1"))

        verify {
            federationReplicator.replicaPermissionChangeTo(
                mockReplicaClient, "perm-id", "proj-1", false, null, null, "remote-node"
            )
        }
    }

    @Test
    fun `PERMISSION_DELETED should forward permName and resourceType from event data`() {
        setupProjectCluster("proj-1", "remote-node")
        every {
            federationReplicator.replicaPermissionChangeTo(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns Unit
        val eventData = mapOf("permName" to "my-perm", "resourceType" to "REPO")

        consumer.action(buildMessage(EventType.PERMISSION_DELETED, "perm-id", "proj-1", data = eventData))

        verify {
            federationReplicator.replicaPermissionChangeTo(
                mockReplicaClient, "perm-id", "proj-1", true, "my-perm", "REPO", "remote-node"
            )
        }
    }

    @Test
    fun `ACCOUNT_CREATE should call replicaAccountChangeTo with deleted=false`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaAccountChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.ACCOUNT_CREATE, "app-id-1", "proj"))

        verify { federationReplicator.replicaAccountChangeTo(mockReplicaClient, "app-id-1", false, "remote-node") }
    }

    @Test
    fun `ACCOUNT_DELETE should call replicaAccountChangeTo with deleted=true`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaAccountChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.ACCOUNT_DELETE, "app-id-1", "proj"))

        verify { federationReplicator.replicaAccountChangeTo(mockReplicaClient, "app-id-1", true, "remote-node") }
    }

    @Test
    fun `KEYS_CREATE should extract userId from event data and call replicaKeyChangeTo`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaKeyChangeTo(any(), any(), any(), any(), any()) } returns Unit
        val eventData = mapOf("userId" to "alice")

        consumer.action(buildMessage(EventType.KEYS_CREATE, "fp:ab:cd", "proj", data = eventData))

        verify { federationReplicator.replicaKeyChangeTo(mockReplicaClient, "fp:ab:cd", "alice", false, "remote-node") }
    }

    @Test
    fun `KEYS_DELETE should fallback to event userId when data has no userId`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaKeyChangeTo(any(), any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.KEYS_DELETE, "fp:ab:cd", "proj", userId = "bob"))

        verify { federationReplicator.replicaKeyChangeTo(mockReplicaClient, "fp:ab:cd", "bob", true, "remote-node") }
    }

    @Test
    fun `OAUTH_TOKEN_CREATED should call replicaOauthTokenChangeTo with deleted=false`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaOauthTokenChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.OAUTH_TOKEN_CREATED, "token-abc", "proj"))

        verify { federationReplicator.replicaOauthTokenChangeTo(mockReplicaClient, "token-abc", false, "remote-node") }
    }

    @Test
    fun `OAUTH_TOKEN_DELETED should call replicaOauthTokenChangeTo with deleted=true`() {
        setupGlobalCluster("remote-node")
        every { federationReplicator.replicaOauthTokenChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.OAUTH_TOKEN_DELETED, "token-abc", "proj"))

        verify { federationReplicator.replicaOauthTokenChangeTo(mockReplicaClient, "token-abc", true, "remote-node") }
    }

    @Test
    fun `TEMP_TOKEN_CREATED should call replicaTemporaryTokenChangeTo with deleted=false`() {
        setupProjectCluster("proj-1", "remote-node")
        every { federationReplicator.replicaTemporaryTokenChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.TEMP_TOKEN_CREATED, "tok-xyz", "proj-1"))

        verify {
            federationReplicator.replicaTemporaryTokenChangeTo(
                mockReplicaClient, "tok-xyz", false, "remote-node"
            )
        }
    }

    @Test
    fun `TEMP_TOKEN_DELETED should call replicaTemporaryTokenChangeTo with deleted=true`() {
        setupProjectCluster("proj-1", "remote-node")
        every { federationReplicator.replicaTemporaryTokenChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.TEMP_TOKEN_DELETED, "tok-xyz", "proj-1"))

        verify { federationReplicator.replicaTemporaryTokenChangeTo(mockReplicaClient, "tok-xyz", true, "remote-node") }
    }

    @Test
    fun `PROXY_CREATED should call replicaProxyChangeTo with deleted=false`() {
        setupProjectCluster("proj-1", "remote-node")
        every { federationReplicator.replicaProxyChangeTo(any(), any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.PROXY_CREATED, "proxy-a", "proj-1"))

        verify {
            federationReplicator.replicaProxyChangeTo(
                mockReplicaClient, "proxy-a", "proj-1", false, "remote-node"
            )
        }
    }

    @Test
    fun `PROXY_DELETED should call replicaProxyChangeTo with deleted=true`() {
        setupProjectCluster("proj-1", "remote-node")
        every { federationReplicator.replicaProxyChangeTo(any(), any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.PROXY_DELETED, "proxy-a", "proj-1"))

        verify {
            federationReplicator.replicaProxyChangeTo(
                mockReplicaClient, "proxy-a", "proj-1", true, "remote-node"
            )
        }
    }

    @Test
    fun `REPO_AUTH_CONFIG_UPDATED should call replicaRepoAuthConfigChangeTo`() {
        setupProjectCluster("proj-1", "remote-node")
        every { federationReplicator.replicaRepoAuthConfigChangeTo(any(), any(), any(), any()) } returns Unit

        consumer.action(buildMessage(EventType.REPO_AUTH_CONFIG_UPDATED, "my-repo", "proj-1", repoName = "my-repo"))

        verify {
            federationReplicator.replicaRepoAuthConfigChangeTo(
                mockReplicaClient, "proj-1", "my-repo", "remote-node"
            )
        }
    }

    // ==================== helper functions ====================

    /** Setup single remote cluster via GLOBAL route (federation group) */
    private fun setupGlobalCluster(clusterName: String) {
        val group = buildFederationGroup(currentClusterId = "local", clusterIds = listOf("local", "remote-id"))
        every { federationGroupService.listAll() } returns listOf(group)
        every { clusterNodeService.getByClusterId("remote-id") } returns buildClusterNodeInfo("remote-id", clusterName)
    }

    /** Setup single remote cluster via PROJECT route (federated repository) */
    private fun setupProjectCluster(projectId: String, clusterName: String) {
        val group = buildFederationGroup(currentClusterId = "local", clusterIds = listOf("local", "remote-id"))
        every { federationGroupService.listAll() } returns listOf(group)
        val repo = buildFederatedRepository(projectId, listOf("remote-id"))
        every { federatedRepositoryDao.findByProjectId(projectId) } returns listOf(repo)
        every { clusterNodeService.getByClusterId("remote-id") } returns buildClusterNodeInfo("remote-id", clusterName)
    }

    private fun buildMessage(
        type: EventType,
        resourceKey: String,
        projectId: String,
        repoName: String = "repo",
        userId: String = "system",
        data: Map<String, Any> = emptyMap()
    ): Message<ArtifactEvent> {
        val event = ArtifactEvent(
            type = type,
            projectId = projectId,
            repoName = repoName,
            resourceKey = resourceKey,
            userId = userId,
            data = data
        )
        val message = mockk<Message<ArtifactEvent>>()
        every { message.payload } returns event
        return message
    }

    private fun buildFederationGroup(
        name: String = "test-group",
        currentClusterId: String = "local",
        clusterIds: List<String> = listOf("local", "remote-1")
    ) = TFederationGroup(
        id = "group-id-$name",
        name = name,
        currentClusterId = currentClusterId,
        clusterIds = clusterIds,
        createdBy = "admin",
        lastModifiedBy = "admin"
    )

    private fun buildFederatedRepository(projectId: String, clusterIds: List<String>) = TFederatedRepository(
        id = "repo-id-$projectId",
        projectId = projectId,
        repoName = "test-repo",
        clusterId = "local",
        federationId = "federation-1",
        name = "federation-repo-$projectId",
        federatedClusters = clusterIds.map {
            FederatedCluster(projectId = projectId, repoName = "test-repo", clusterId = it)
        },
        createdBy = "admin",
        createdDate = LocalDateTime.now(),
        lastModifiedBy = "admin",
        lastModifiedDate = LocalDateTime.now()
    )

    private fun buildClusterNodeInfo(id: String, name: String = "cluster-$id") = ClusterNodeInfo(
        id = id,
        name = name,
        status = ClusterNodeStatus.HEALTHY,
        errorReason = null,
        type = ClusterNodeType.STANDALONE,
        url = "http://$name:8080",
        username = null,
        password = null,
        certificate = null,
        createdBy = "admin",
        createdDate = "2024-01-01",
        lastModifiedBy = "admin",
        lastModifiedDate = "2024-01-01",
        detectType = DetectType.PING,
        lastReportTime = null
    )
}
