package com.tencent.bkrepo.replication.controller.service

import com.tencent.bkrepo.auth.api.ServiceAccountClient
import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServiceKeyClient
import com.tencent.bkrepo.auth.api.ServiceOauthAuthorizationClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceProxyClient
import com.tencent.bkrepo.auth.api.ServiceRepoModeClient
import com.tencent.bkrepo.auth.api.ServiceRoleClient
import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.account.AccountInfo
import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode
import com.tencent.bkrepo.auth.pojo.externalPermission.ExternalPermission
import com.tencent.bkrepo.auth.pojo.key.KeyInfo
import com.tencent.bkrepo.auth.pojo.oauth.OauthTokenInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatus
import com.tencent.bkrepo.auth.pojo.role.RoleInfo
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.context.FederationReplicaContext
import com.tencent.bkrepo.replication.pojo.request.AccountReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ExternalPermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.KeyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.OauthTokenReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PersonalPathReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ProxyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaAction
import com.tencent.bkrepo.replication.pojo.request.RepoAuthConfigReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.TemporaryTokenReplicaRequest
import io.micrometer.tracing.Tracer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.time.Instant

/**
 * ArtifactReplicaController 新增实体接收端测试（对齐当前实现）
 *
 * 覆盖每个新实体的 UPSERT/DELETE 幂等策略：
 * - TRole: 不存在则 createRoleForFederation，存在则 updateRoleForFederation
 * - TAccount: 不存在则 createAccountForFederation，存在则 updateAccountForFederation
 * - TExternalPermission: 不存在则创建，存在则更新
 * - TTemporaryToken: 不存在则创建，已存在则跳过
 * - TOauthToken: 调用 createOauthTokenForFederation（幂等由服务层保证）
 * - TPersonalPath: 调用 createPersonalPath（幂等由服务层保证）
 * - TProxy: 不存在则 createProxyForFederation，存在则 updateProxyForFederation
 * - TKey: 按 fingerprint 幂等，不存在则 createKeyForFederation
 * - TRepoAuthConfig: 始终调用 upsertRepoAuthConfig
 */
@ExtendWith(MockKExtension::class)
class ArtifactReplicaControllerNewEntitiesTest {

    @MockK
    private lateinit var localRoleClient: ServiceRoleClient

    @MockK
    private lateinit var localAccountClient: ServiceAccountClient

    @MockK
    private lateinit var localExternalPermissionClient: ServiceExternalPermissionClient

    @MockK
    private lateinit var localTemporaryTokenClient: ServiceTemporaryTokenClient

    @MockK
    private lateinit var localOauthAuthorizationClient: ServiceOauthAuthorizationClient

    @MockK
    private lateinit var localPermissionClient: ServicePermissionClient

    @MockK
    private lateinit var localProxyClient: ServiceProxyClient

    @MockK
    private lateinit var localKeyClient: ServiceKeyClient

    @MockK
    private lateinit var localRepoModeClient: ServiceRepoModeClient

    @MockK
    private lateinit var userResource: ServiceUserClient

    private lateinit var controller: ArtifactReplicaController

    @BeforeEach
    fun setUp() {
        mockkObject(SpringContextUtils)
        every { SpringContextUtils.getBean<Tracer>() } returns mockk(relaxed = true)
        controller = ArtifactReplicaController(
            projectService = mockk(relaxed = true),
            repositoryService = mockk(relaxed = true),
            nodeService = mockk(relaxed = true),
            packageService = mockk(relaxed = true),
            metadataService = mockk(relaxed = true),
            packageMetadataService = mockk(relaxed = true),
            userResource = userResource,
            permissionManager = mockk(relaxed = true),
            blockNodeService = mockk(relaxed = true),
            localDataManager = mockk(relaxed = true),
            localPermissionClient = localPermissionClient,
            localRoleClient = localRoleClient,
            localAccountClient = localAccountClient,
            localExternalPermissionClient = localExternalPermissionClient,
            localTemporaryTokenClient = localTemporaryTokenClient,
            localOauthAuthorizationClient = localOauthAuthorizationClient,
            localProxyClient = localProxyClient,
            localKeyClient = localKeyClient,
            localRepoModeClient = localRepoModeClient,
        )
    }

    @AfterEach
    fun tearDown() {
        FederationReplicaContext.clear()
        unmockkObject(SpringContextUtils)
    }

    // ==================== replicaRoleRequest ====================

    @Test
    fun `replicaRoleRequest UPSERT - role not exists should createRoleForFederation`() {
        val request = RoleReplicaRequest(
            action = ReplicaAction.UPSERT,
            roleId = "new-role",
            name = "New Role",
            type = "PROJECT",
            projectId = PROJECT_ID
        )
        every { localRoleClient.listRoleByProject(PROJECT_ID) } returns ok(emptyList())
        every { localRoleClient.createRoleForFederation(any()) } returns ok("new-id")

        controller.replicaRoleRequest(request)

        verify(exactly = 1) { localRoleClient.createRoleForFederation(any()) }
        verify(exactly = 0) { localRoleClient.updateRoleForFederation(any()) }
    }

    @Test
    fun `replicaRoleRequest UPSERT - role exists should updateRoleForFederation with existing id`() {
        val existing = buildRoleInfo("existing-role", "PROJECT", id = "role-id-1")
        val request = RoleReplicaRequest(
            action = ReplicaAction.UPSERT,
            roleId = "existing-role",
            name = "Updated Role Name",
            type = "PROJECT",
            projectId = PROJECT_ID,
            admin = true
        )
        every { localRoleClient.listRoleByProject(PROJECT_ID) } returns ok(listOf(existing))
        every { localRoleClient.updateRoleForFederation(any()) } returns ok(true)

        controller.replicaRoleRequest(request)

        verify(exactly = 0) { localRoleClient.createRoleForFederation(any()) }
        val slot = slot<RoleInfo>()
        verify(exactly = 1) { localRoleClient.updateRoleForFederation(capture(slot)) }
        assertEquals("role-id-1", slot.captured.id)
    }

    @Test
    fun `replicaRoleRequest DELETE - id provided should deleteRoleForFederation`() {
        val request = RoleReplicaRequest(
            action = ReplicaAction.DELETE,
            id = "role-del-id",
            roleId = "del-role",
            type = "PROJECT",
            projectId = PROJECT_ID
        )
        every { localRoleClient.deleteRoleForFederation("role-del-id") } returns ok(true)

        controller.replicaRoleRequest(request)

        verify(exactly = 1) { localRoleClient.deleteRoleForFederation("role-del-id") }
    }

    @Test
    fun `replicaRoleRequest DELETE - id null should log warning and skip`() {
        val request = RoleReplicaRequest(
            action = ReplicaAction.DELETE,
            roleId = "ghost-role",
            type = "PROJECT",
            projectId = PROJECT_ID
        )

        controller.replicaRoleRequest(request)

        verify(exactly = 0) { localRoleClient.deleteRoleForFederation(any()) }
    }

    // ==================== replicaAccountRequest ====================

    @Test
    fun `replicaAccountRequest UPSERT - account not exists should upsertAccountForFederation`() {
        val request = AccountReplicaRequest(
            action = ReplicaAction.UPSERT,
            appId = "new-app",
            locked = false,
            authorizationGrantTypes = setOf("PLATFORM")
        )
        every { localAccountClient.upsertAccountForFederation(any()) } returns mockk(relaxed = true)

        controller.replicaAccountRequest(request)

        verify(exactly = 1) { localAccountClient.upsertAccountForFederation(any()) }
    }

    @Test
    fun `replicaAccountRequest UPSERT - account exists should upsertAccountForFederation`() {
        val request = AccountReplicaRequest(
            action = ReplicaAction.UPSERT,
            appId = "exist-app",
            locked = true,
            authorizationGrantTypes = setOf("PLATFORM")
        )
        every { localAccountClient.upsertAccountForFederation(any()) } returns mockk(relaxed = true)

        controller.replicaAccountRequest(request)

        verify(exactly = 1) { localAccountClient.upsertAccountForFederation(any()) }
    }

    @Test
    fun `replicaAccountRequest DELETE - should deleteAccountForFederation`() {
        val request = AccountReplicaRequest(
            action = ReplicaAction.DELETE,
            appId = "del-app"
        )
        every { localAccountClient.deleteAccountForFederation("del-app") } returns ok(true)

        controller.replicaAccountRequest(request)

        verify(exactly = 1) { localAccountClient.deleteAccountForFederation("del-app") }
    }

    // ==================== replicaExternalPermissionRequest ====================

    @Test
    fun `replicaExternalPermissionRequest UPSERT - not exists should createExternalPermission`() {
        val request = ExternalPermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            url = "https://ext.example.com",
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            scope = "generic",
            enabled = true
        )
        every { localExternalPermissionClient.listExternalPermission() } returns ok(emptyList())
        every { localExternalPermissionClient.createExternalPermission(any()) } returns ok(true)

        controller.replicaExternalPermissionRequest(request)

        verify(exactly = 1) { localExternalPermissionClient.createExternalPermission(any()) }
        verify(exactly = 0) { localExternalPermissionClient.updateExternalPermission(any()) }
    }

    @Test
    fun `replicaExternalPermissionRequest UPSERT - exists should updateExternalPermission with existing id`() {
        val existing = buildExternalPermission(PROJECT_ID, REPO_NAME, id = "ext-id-1")
        val request = ExternalPermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            url = "https://new-ext.example.com",
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            scope = "generic",
            enabled = false
        )
        every { localExternalPermissionClient.listExternalPermission() } returns ok(listOf(existing))
        every { localExternalPermissionClient.updateExternalPermission(any()) } returns ok(true)

        controller.replicaExternalPermissionRequest(request)

        verify(exactly = 0) { localExternalPermissionClient.createExternalPermission(any()) }
        verify(exactly = 1) { localExternalPermissionClient.updateExternalPermission(any()) }
    }

    @Test
    fun `replicaExternalPermissionRequest DELETE - id provided should delete`() {
        val request = ExternalPermissionReplicaRequest(
            action = ReplicaAction.DELETE,
            id = "ext-del-id",
            projectId = PROJECT_ID,
            repoName = REPO_NAME
        )
        every { localExternalPermissionClient.deleteExternalPermission("ext-del-id") } returns ok(true)

        controller.replicaExternalPermissionRequest(request)

        verify(exactly = 1) { localExternalPermissionClient.deleteExternalPermission("ext-del-id") }
    }

    @Test
    fun `replicaExternalPermissionRequest DELETE - id null should log warning and skip`() {
        val request = ExternalPermissionReplicaRequest(
            action = ReplicaAction.DELETE,
            projectId = PROJECT_ID,
            repoName = REPO_NAME
        )

        controller.replicaExternalPermissionRequest(request)

        verify(exactly = 0) { localExternalPermissionClient.deleteExternalPermission(any()) }
    }

    // ==================== replicaTemporaryTokenRequest ====================

    @Test
    fun `replicaTemporaryTokenRequest UPSERT - token not exists should createToken`() {
        val request = TemporaryTokenReplicaRequest(
            action = ReplicaAction.UPSERT,
            token = "tok-new",
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/file.zip",
            authorizedUserList = setOf("alice"),
            authorizedIpList = emptySet(),
            permits = 3,
            type = "DOWNLOAD",
            createdBy = "admin"
        )
        every { localTemporaryTokenClient.getTokenInfo("tok-new") } returns ok(null)
        every { localTemporaryTokenClient.createToken(any()) } returns ok(listOf(buildTemporaryToken("tok-new")))

        controller.replicaTemporaryTokenRequest(request)

        verify(exactly = 1) { localTemporaryTokenClient.createToken(any()) }
    }

    @Test
    fun `replicaTemporaryTokenRequest UPSERT - token already exists should skip`() {
        val existing = buildTemporaryToken("tok-exists")
        val request = TemporaryTokenReplicaRequest(
            action = ReplicaAction.UPSERT,
            token = "tok-exists",
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/file.zip",
            authorizedUserList = emptySet(),
            authorizedIpList = emptySet(),
            permits = 10,
            type = "DOWNLOAD",
            createdBy = "admin"
        )
        every { localTemporaryTokenClient.getTokenInfo("tok-exists") } returns ok(existing)

        controller.replicaTemporaryTokenRequest(request)

        verify(exactly = 0) { localTemporaryTokenClient.createToken(any()) }
    }

    @Test
    fun `replicaTemporaryTokenRequest UPSERT - already expired expireDate should skip`() {
        val request = TemporaryTokenReplicaRequest(
            action = ReplicaAction.UPSERT,
            token = "tok-expired",
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/file.zip",
            authorizedUserList = emptySet(),
            authorizedIpList = emptySet(),
            // 过去时间，remaining < 0
            expireDate = java.time.LocalDateTime.now().minusHours(1)
                .format(java.time.format.DateTimeFormatter.ISO_DATE_TIME),
            type = "DOWNLOAD",
            createdBy = "admin"
        )
        every { localTemporaryTokenClient.getTokenInfo("tok-expired") } returns ok(null)

        controller.replicaTemporaryTokenRequest(request)

        verify(exactly = 0) { localTemporaryTokenClient.createToken(any()) }
    }

    @Test
    fun `replicaTemporaryTokenRequest DELETE - should deleteToken`() {
        val request = TemporaryTokenReplicaRequest(
            action = ReplicaAction.DELETE,
            token = "tok-del"
        )
        every { localTemporaryTokenClient.deleteToken("tok-del") } returns ok()

        controller.replicaTemporaryTokenRequest(request)

        verify(exactly = 1) { localTemporaryTokenClient.deleteToken("tok-del") }
    }

    // ==================== replicaOauthTokenRequest ====================

    @Test
    fun `replicaOauthTokenRequest UPSERT - should call createOauthTokenForFederation`() {
        val request = OauthTokenReplicaRequest(
            action = ReplicaAction.UPSERT,
            accessToken = "access-new",
            accountId = "app-1",
            userId = "alice",
            scope = setOf("read"),
            issuedAt = Instant.now().epochSecond
        )
        every { localOauthAuthorizationClient.createOauthTokenForFederation(any()) } returns ok()

        controller.replicaOauthTokenRequest(request)

        verify(exactly = 1) { localOauthAuthorizationClient.createOauthTokenForFederation(any()) }
    }

    @Test
    fun `replicaOauthTokenRequest UPSERT - should pass correct fields to createOauthTokenForFederation`() {
        val issuedAt = Instant.now().epochSecond
        val request = OauthTokenReplicaRequest(
            action = ReplicaAction.UPSERT,
            accessToken = "tok-abc",
            refreshToken = "ref-abc",
            expireSeconds = 3600L,
            type = "Bearer",
            accountId = "app-x",
            userId = "eve",
            scope = setOf("read", "write"),
            issuedAt = issuedAt
        )
        val slot = slot<OauthTokenInfo>()
        every { localOauthAuthorizationClient.createOauthTokenForFederation(capture(slot)) } returns ok()

        controller.replicaOauthTokenRequest(request)

        assertEquals("tok-abc", slot.captured.accessToken)
        assertEquals("ref-abc", slot.captured.refreshToken)
        assertEquals(3600L, slot.captured.expireSeconds)
        assertEquals("Bearer", slot.captured.type)
        assertEquals("app-x", slot.captured.accountId)
        assertEquals("eve", slot.captured.userId)
        assertTrue(slot.captured.scope!!.contains("read"))
        assertTrue(slot.captured.scope!!.contains("write"))
        assertEquals(issuedAt, slot.captured.issuedAt)
    }

    @Test
    fun `replicaOauthTokenRequest DELETE - should call deleteOauthTokenForFederation`() {
        val request = OauthTokenReplicaRequest(
            action = ReplicaAction.DELETE,
            accessToken = "access-del"
        )
        every { localOauthAuthorizationClient.deleteOauthTokenForFederation("access-del") } returns ok()

        controller.replicaOauthTokenRequest(request)

        verify(exactly = 1) { localOauthAuthorizationClient.deleteOauthTokenForFederation("access-del") }
    }

    // ==================== replicaPersonalPathRequest ====================

    @Test
    fun `replicaPersonalPathRequest UPSERT - should call createPersonalPath`() {
        val request = PersonalPathReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "carol",
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/personal/carol"
        )
        every { localPermissionClient.createPersonalPath(any()) } returns ok(true)

        controller.replicaPersonalPathRequest(request)

        verify(exactly = 1) { localPermissionClient.createPersonalPath(any()) }
    }

    @Test
    fun `replicaPersonalPathRequest DELETE - should call deletePersonalPath`() {
        val request = PersonalPathReplicaRequest(
            action = ReplicaAction.DELETE,
            userId = "carol",
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            fullPath = "/personal/carol"
        )
        every { localPermissionClient.deletePersonalPath(PROJECT_ID, REPO_NAME, "carol") } returns ok(true)

        controller.replicaPersonalPathRequest(request)

        verify(exactly = 1) { localPermissionClient.deletePersonalPath(PROJECT_ID, REPO_NAME, "carol") }
    }

    // ==================== replicaProxyRequest ====================

    @Test
    fun `replicaProxyRequest UPSERT - proxy not exists should createProxyForFederation`() {
        val request = ProxyReplicaRequest(
            action = ReplicaAction.UPSERT,
            name = "new-proxy",
            displayName = "New Proxy",
            projectId = PROJECT_ID,
            domain = "proxy.example.com",
            syncRateLimit = 0L,
            syncTimeRange = "00:00-23:59",
            cacheExpireDays = 7
        )
        every { localProxyClient.listProxyByProject(PROJECT_ID) } returns ok(emptyList())
        every { localProxyClient.createProxyForFederation(any()) } returns ok(true)

        controller.replicaProxyRequest(request)

        verify(exactly = 1) { localProxyClient.createProxyForFederation(any()) }
        verify(exactly = 0) { localProxyClient.updateProxyForFederation(any()) }
    }

    @Test
    fun `replicaProxyRequest UPSERT - proxy exists should updateProxyForFederation`() {
        val existing = buildProxyInfo("edge-proxy", PROJECT_ID)
        val request = ProxyReplicaRequest(
            action = ReplicaAction.UPSERT,
            name = "edge-proxy",
            displayName = "Edge Proxy Updated",
            projectId = PROJECT_ID,
            domain = "new-proxy.example.com",
            syncRateLimit = 5 * 1024 * 1024L,
            syncTimeRange = "02:00-04:00",
            cacheExpireDays = 14
        )
        every { localProxyClient.listProxyByProject(PROJECT_ID) } returns ok(listOf(existing))
        every { localProxyClient.updateProxyForFederation(any()) } returns ok(true)

        controller.replicaProxyRequest(request)

        verify(exactly = 0) { localProxyClient.createProxyForFederation(any()) }
        verify(exactly = 1) { localProxyClient.updateProxyForFederation(any()) }
    }

    @Test
    fun `replicaProxyRequest DELETE - should deleteProxyForFederation`() {
        val request = ProxyReplicaRequest(
            action = ReplicaAction.DELETE,
            name = "del-proxy",
            projectId = PROJECT_ID
        )
        every { localProxyClient.deleteProxyForFederation(PROJECT_ID, "del-proxy") } returns ok(true)

        controller.replicaProxyRequest(request)

        verify(exactly = 1) { localProxyClient.deleteProxyForFederation(PROJECT_ID, "del-proxy") }
    }

    // ==================== replicaKeyRequest ====================

    @Test
    fun `replicaKeyRequest UPSERT - key not exists should createKeyForFederation`() {
        val request = KeyReplicaRequest(
            action = ReplicaAction.UPSERT,
            id = "key-id-1",
            name = "my-key",
            key = "ssh-rsa AAAAB3NzaC1yc2EA user@host",
            fingerprint = "ab:cd:ef:01:23:45:67:89",
            userId = "alice",
            createAt = LocalDateTime.now().toString()
        )
        every { localKeyClient.listKeyByUserId("alice") } returns ok(emptyList())
        every { localKeyClient.createKeyForFederation(any()) } returns ok(true)

        controller.replicaKeyRequest(request)

        verify(exactly = 1) { localKeyClient.createKeyForFederation(any()) }
    }

    @Test
    fun `replicaKeyRequest UPSERT - should pass correct KeyInfo fields to createKeyForFederation`() {
        val createAt = LocalDateTime.now().toString()
        val request = KeyReplicaRequest(
            action = ReplicaAction.UPSERT,
            id = "key-id-x",
            name = "my-ssh-key",
            key = "ssh-rsa AAAA... alice@host",
            fingerprint = "11:22:33:44",
            userId = "alice",
            createAt = createAt
        )
        every { localKeyClient.listKeyByUserId("alice") } returns ok(emptyList())
        val slot = slot<KeyInfo>()
        every { localKeyClient.createKeyForFederation(capture(slot)) } returns ok(true)

        controller.replicaKeyRequest(request)

        assertEquals("key-id-x", slot.captured.id)
        assertEquals("my-ssh-key", slot.captured.name)
        assertEquals("ssh-rsa AAAA... alice@host", slot.captured.key)
        assertEquals("11:22:33:44", slot.captured.fingerprint)
        assertEquals("alice", slot.captured.userId)
    }

    @Test
    fun `replicaKeyRequest UPSERT - key already exists (same fingerprint) should skip`() {
        val existing = buildKeyInfo("alice", "ab:cd:ef:01:23:45:67:89")
        val request = KeyReplicaRequest(
            action = ReplicaAction.UPSERT,
            id = "key-id-1",
            name = "my-key",
            key = "ssh-rsa AAAAB3NzaC1yc2EA user@host",
            fingerprint = "ab:cd:ef:01:23:45:67:89",
            userId = "alice",
            createAt = LocalDateTime.now().toString()
        )
        every { localKeyClient.listKeyByUserId("alice") } returns ok(listOf(existing))

        controller.replicaKeyRequest(request)

        verify(exactly = 0) { localKeyClient.createKeyForFederation(any()) }
    }

    @Test
    fun `replicaKeyRequest DELETE - id blank should log warning and skip`() {
        val request = KeyReplicaRequest(
            action = ReplicaAction.DELETE,
            id = "",
            fingerprint = "ab:cd:ef:01",
            userId = "alice"
        )

        controller.replicaKeyRequest(request)

        verify(exactly = 0) { localKeyClient.deleteKeyForFederation(any()) }
    }

    @Test
    fun `replicaKeyRequest DELETE - id provided should deleteKeyForFederation`() {
        val request = KeyReplicaRequest(
            action = ReplicaAction.DELETE,
            id = "key-del-id",
            fingerprint = "ab:cd:ef:01",
            userId = "alice"
        )
        val existingKey = buildKeyInfo("alice", "ab:cd:ef:01").copy(id = "key-del-id")
        every { localKeyClient.listKeyByUserId("alice") } returns ok(listOf(existingKey))
        every { localKeyClient.deleteKeyForFederation("key-del-id") } returns ok(true)

        controller.replicaKeyRequest(request)

        verify(exactly = 1) { localKeyClient.deleteKeyForFederation("key-del-id") }
    }

    // ==================== replicaRepoAuthConfigRequest ====================

    @Test
    fun `replicaRepoAuthConfigRequest UPSERT - should call upsertRepoAuthConfig`() {
        val request = RepoAuthConfigReplicaRequest(
            action = ReplicaAction.UPSERT,
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            accessControlMode = "DEFAULT",
            bkiamv3Check = false
        )
        every {
            localRepoModeClient.upsertRepoAuthConfig(PROJECT_ID, REPO_NAME, AccessControlMode.DEFAULT, any(), false)
        } returns ok(true)

        controller.replicaRepoAuthConfigRequest(request)

        verify(exactly = 1) {
            localRepoModeClient.upsertRepoAuthConfig(PROJECT_ID, REPO_NAME, AccessControlMode.DEFAULT, any(), false)
        }
    }

    @Test
    fun `replicaRepoAuthConfigRequest UPSERT - invalid accessControlMode should default to DEFAULT`() {
        val request = RepoAuthConfigReplicaRequest(
            action = ReplicaAction.UPSERT,
            projectId = PROJECT_ID,
            repoName = REPO_NAME,
            accessControlMode = "INVALID_MODE",
            bkiamv3Check = true
        )
        every {
            localRepoModeClient.upsertRepoAuthConfig(PROJECT_ID, REPO_NAME, AccessControlMode.DEFAULT, any(), true)
        } returns ok(true)

        controller.replicaRepoAuthConfigRequest(request)

        verify(exactly = 1) {
            localRepoModeClient.upsertRepoAuthConfig(PROJECT_ID, REPO_NAME, AccessControlMode.DEFAULT, any(), true)
        }
    }

    @Test
    fun `replicaRepoAuthConfigRequest DELETE - should skip (not supported)`() {
        val request = RepoAuthConfigReplicaRequest(
            action = ReplicaAction.DELETE,
            projectId = PROJECT_ID,
            repoName = REPO_NAME
        )

        controller.replicaRepoAuthConfigRequest(request)

        verify(exactly = 0) { localRepoModeClient.upsertRepoAuthConfig(any(), any(), any(), any(), any()) }
    }

    // ==================== helper builders ====================

    private fun <T> ok(data: T? = null) = Response(CommonMessageCode.SUCCESS.getCode(), data = data)

    private fun buildRoleInfo(roleId: String, type: String, id: String = "id-$roleId") = RoleInfo(
        id = id,
        roleId = roleId,
        name = "name-$roleId",
        type = type,
        projectId = PROJECT_ID,
        admin = false
    )

    private fun buildAccountInfo(appId: String) = AccountInfo(
        id = "id-$appId",
        appId = appId,
        locked = false,
        authorizationGrantTypes = setOf("PLATFORM"),
        description = "test app"
    )

    private fun buildExternalPermission(
        projectId: String,
        repoName: String,
        id: String = "id-$projectId-$repoName"
    ) = ExternalPermission(
        id = id,
        url = "https://ext.example.com",
        headers = emptyMap(),
        projectId = projectId,
        repoName = repoName,
        scope = "generic",
        enabled = true,
        createdDate = LocalDateTime.now(),
        createdBy = "admin",
        lastModifiedDate = LocalDateTime.now(),
        lastModifiedBy = "admin"
    )

    private fun buildTemporaryToken(
        token: String,
        projectId: String = PROJECT_ID,
        repoName: String = REPO_NAME,
        fullPath: String = "/file.zip",
        permits: Int? = null
    ) = TemporaryTokenInfo(
        projectId = projectId,
        repoName = repoName,
        fullPath = fullPath,
        token = token,
        authorizedUserList = emptySet(),
        authorizedIpList = emptySet(),
        expireDate = null,
        permits = permits,
        type = TokenType.DOWNLOAD,
        createdBy = "admin"
    )

    private fun buildKeyInfo(userId: String, fingerprint: String) = KeyInfo(
        id = "key-id",
        name = "test-key",
        key = "ssh-rsa AAAAB3NzaC1yc2EA $userId@host",
        fingerprint = fingerprint,
        userId = userId,
        createAt = LocalDateTime.now()
    )

    private fun buildProxyInfo(name: String, projectId: String) = ProxyInfo(
        name = name,
        displayName = name,
        projectId = projectId,
        clusterName = "cluster-a",
        domain = "proxy.example.com",
        ip = "192.168.1.1",
        status = ProxyStatus.ONLINE,
        syncRateLimit = 0L,
        syncTimeRange = "00:00-23:59",
        cacheExpireDays = 7
    )

    companion object {
        const val PROJECT_ID = "test-project"
        const val REPO_NAME = "test-repo"
    }
}
