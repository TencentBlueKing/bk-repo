package com.tencent.bkrepo.replication.replica.replicator.standalone

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
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.permission.PersonalPathInfo
import com.tencent.bkrepo.auth.pojo.permission.RepoAuthConfigInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatus
import com.tencent.bkrepo.auth.pojo.role.RoleInfo
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.pojo.request.AccountReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ExternalPermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.KeyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.OauthTokenReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PersonalPathReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ProxyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RepoAuthConfigReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.TemporaryTokenReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.time.Instant

/**
 * FederationReplicator 新增实体全量同步源端测试
 *
 * 使用 TestHelper 模拟 FederationReplicator 内部逻辑，对准真实 API：
 * - listRoleByProject / listAccountsForFederation / listExternalPermission
 * - listActiveByProject(projectId) / listActiveTokens() / listPersonalPath(projectId)
 * - listProxyByProject(projectId) / listKeyByUserId(userId) / listByProject(projectId)
 */
@ExtendWith(MockKExtension::class)
class FederationReplicatorNewEntitiesTest {

    @MockK
    private lateinit var localRoleClient: ServiceRoleClient

    @MockK
    private lateinit var localAccountClient: ServiceAccountClient

    @MockK
    private lateinit var localExternalPermissionClient: ServiceExternalPermissionClient

    @MockK
    private lateinit var localTemporaryTokenClient: ServiceTemporaryTokenClient

    @MockK
    private lateinit var localOauthTokenClient: ServiceOauthAuthorizationClient

    @MockK
    private lateinit var localPermissionClient: ServicePermissionClient

    @MockK
    private lateinit var localProxyClient: ServiceProxyClient

    @MockK
    private lateinit var localKeyClient: ServiceKeyClient

    @MockK
    private lateinit var localRepoModeClient: ServiceRepoModeClient

    @MockK
    private lateinit var localUserClient: ServiceUserClient

    @MockK
    private lateinit var artifactReplicaClient: ArtifactReplicaClient

    private lateinit var context: ReplicaContext

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.artifactReplicaClient } returns artifactReplicaClient
        every { context.localProjectId } returns PROJECT_ID
        every { context.localRepoName } returns REPO_NAME
        every { context.remoteCluster.name } returns "remote-cluster"
    }

    // ==================== replicaRoles ====================

    @Test
    fun `replicaRoles - should fetch roles by project and push each to target`() {
        val roles = listOf(buildRoleInfo("role-1", "PROJECT"), buildRoleInfo("role-2", "REPO"))
        every { localRoleClient.listRoleByProject(PROJECT_ID) } returns ok(roles)
        every { artifactReplicaClient.replicaRoleRequest(any()) } returns ok()

        buildReplicator().replicaRoles(context)

        verify(exactly = 2) { artifactReplicaClient.replicaRoleRequest(any()) }
    }

    @Test
    fun `replicaRoles - should push correct role fields`() {
        val role = buildRoleInfo("mgr-role", "PROJECT", admin = true, description = "proj mgr")
        every { localRoleClient.listRoleByProject(PROJECT_ID) } returns ok(listOf(role))
        val slot = slot<RoleReplicaRequest>()
        every { artifactReplicaClient.replicaRoleRequest(capture(slot)) } returns ok()

        buildReplicator().replicaRoles(context)

        assertEquals("mgr-role", slot.captured.roleId)
        assertEquals("PROJECT", slot.captured.type)
        assertEquals(PROJECT_ID, slot.captured.projectId)
        assertTrue(slot.captured.admin)
        assertEquals("proj mgr", slot.captured.description)
    }

    @Test
    fun `replicaRoles - empty role list should not call replicaRoleRequest`() {
        every { localRoleClient.listRoleByProject(PROJECT_ID) } returns ok(emptyList())

        buildReplicator().replicaRoles(context)

        verify(exactly = 0) { artifactReplicaClient.replicaRoleRequest(any()) }
    }

    @Test
    fun `replicaRoles - listRoleByProject exception should be swallowed gracefully`() {
        every { localRoleClient.listRoleByProject(PROJECT_ID) } throws RuntimeException("auth service down")

        buildReplicator().replicaRoles(context)

        verify(exactly = 0) { artifactReplicaClient.replicaRoleRequest(any()) }
    }

    @Test
    fun `replicaRoles - single role push failure should not abort remaining roles`() {
        val roles = listOf(buildRoleInfo("role-ok", "PROJECT"), buildRoleInfo("role-fail", "PROJECT"))
        every { localRoleClient.listRoleByProject(PROJECT_ID) } returns ok(roles)
        every { artifactReplicaClient.replicaRoleRequest(match { it.roleId == "role-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaRoleRequest(match { it.roleId == "role-ok" }) } returns ok()

        buildReplicator().replicaRoles(context)

        verify(exactly = 1) { artifactReplicaClient.replicaRoleRequest(match { it.roleId == "role-ok" }) }
    }

    // ==================== replicaAccounts ====================

    @Test
    fun `replicaAccounts - should fetch all accounts and push each to target`() {
        val accounts = listOf(buildAccountInfo("app-1"), buildAccountInfo("app-2"))
        every { localAccountClient.listAccountsForFederation() } returns ok(accounts)
        every { artifactReplicaClient.replicaAccountRequest(any()) } returns ok()

        buildReplicator().replicaAccounts(context)

        verify(exactly = 2) { artifactReplicaClient.replicaAccountRequest(any()) }
    }

    @Test
    fun `replicaAccounts - should push correct account fields`() {
        val account = buildAccountInfo("my-app", locked = false)
        every { localAccountClient.listAccountsForFederation() } returns ok(listOf(account))
        val slot = slot<AccountReplicaRequest>()
        every { artifactReplicaClient.replicaAccountRequest(capture(slot)) } returns ok()

        buildReplicator().replicaAccounts(context)

        assertEquals("my-app", slot.captured.appId)
        assertEquals(false, slot.captured.locked)
        assertTrue(slot.captured.authorizationGrantTypes.contains("PLATFORM"))
    }

    @Test
    fun `replicaAccounts - empty list should not push`() {
        every { localAccountClient.listAccountsForFederation() } returns ok(emptyList())

        buildReplicator().replicaAccounts(context)

        verify(exactly = 0) { artifactReplicaClient.replicaAccountRequest(any()) }
    }

    @Test
    fun `replicaAccounts - listAccountsForFederation exception should be swallowed`() {
        every { localAccountClient.listAccountsForFederation() } throws RuntimeException("network error")

        buildReplicator().replicaAccounts(context)

        verify(exactly = 0) { artifactReplicaClient.replicaAccountRequest(any()) }
    }

    @Test
    fun `replicaAccounts - single account push failure should not abort remaining accounts`() {
        val accounts = listOf(buildAccountInfo("app-ok"), buildAccountInfo("app-fail"))
        every { localAccountClient.listAccountsForFederation() } returns ok(accounts)
        every { artifactReplicaClient.replicaAccountRequest(match { it.appId == "app-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaAccountRequest(match { it.appId == "app-ok" }) } returns ok()

        buildReplicator().replicaAccounts(context)

        verify(exactly = 1) { artifactReplicaClient.replicaAccountRequest(match { it.appId == "app-ok" }) }
    }

    // ==================== replicaExternalPermissions ====================

    @Test
    fun `replicaExternalPermissions - should fetch all and push each to target`() {
        val extPerms = listOf(
            buildExternalPermission(PROJECT_ID, REPO_NAME),
            buildExternalPermission("other-proj", "other-repo")
        )
        every { localExternalPermissionClient.listExternalPermission() } returns ok(extPerms)
        every { artifactReplicaClient.replicaExternalPermissionRequest(any()) } returns ok()

        buildReplicator().replicaExternalPermissions(context)

        verify(exactly = 2) { artifactReplicaClient.replicaExternalPermissionRequest(any()) }
    }

    @Test
    fun `replicaExternalPermissions - should push correct fields including enabled flag and headers`() {
        val extPerm = buildExternalPermission(
            PROJECT_ID, REPO_NAME,
            url = "https://external.example.com/perm",
            headers = mapOf("X-Token" to "secret"),
            enabled = true
        )
        every { localExternalPermissionClient.listExternalPermission() } returns ok(listOf(extPerm))
        val slot = slot<ExternalPermissionReplicaRequest>()
        every { artifactReplicaClient.replicaExternalPermissionRequest(capture(slot)) } returns ok()

        buildReplicator().replicaExternalPermissions(context)

        assertEquals(PROJECT_ID, slot.captured.projectId)
        assertEquals(REPO_NAME, slot.captured.repoName)
        assertEquals("https://external.example.com/perm", slot.captured.url)
        assertEquals("secret", slot.captured.headers?.get("X-Token"))
        assertTrue(slot.captured.enabled)
    }

    @Test
    fun `replicaExternalPermissions - empty list should not push`() {
        every { localExternalPermissionClient.listExternalPermission() } returns ok(emptyList())

        buildReplicator().replicaExternalPermissions(context)

        verify(exactly = 0) { artifactReplicaClient.replicaExternalPermissionRequest(any()) }
    }

    @Test
    fun `replicaExternalPermissions - exception should be swallowed`() {
        every { localExternalPermissionClient.listExternalPermission() } throws RuntimeException("error")

        buildReplicator().replicaExternalPermissions(context)

        verify(exactly = 0) { artifactReplicaClient.replicaExternalPermissionRequest(any()) }
    }

    @Test
    fun `replicaExternalPermissions - single push failure should not abort remaining`() {
        val extPerms = listOf(
            buildExternalPermission(PROJECT_ID, "repo-ok"),
            buildExternalPermission(PROJECT_ID, "repo-fail")
        )
        every { localExternalPermissionClient.listExternalPermission() } returns ok(extPerms)
        every { artifactReplicaClient.replicaExternalPermissionRequest(match { it.repoName == "repo-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaExternalPermissionRequest(match { it.repoName == "repo-ok" }) } returns ok()

        buildReplicator().replicaExternalPermissions(context)

        verify(exactly = 1) {
            artifactReplicaClient.replicaExternalPermissionRequest(match { it.repoName == "repo-ok" })
        }
    }

    // ==================== replicaTemporaryTokens ====================

    @Test
    fun `replicaTemporaryTokens - should fetch active tokens by project and push each`() {
        val tokens = listOf(
            buildTemporaryToken("token-abc", PROJECT_ID, REPO_NAME, "/path/file.txt"),
            buildTemporaryToken("token-def", PROJECT_ID, REPO_NAME, "/path/other.txt")
        )
        every { localTemporaryTokenClient.listActiveByProject(PROJECT_ID) } returns ok(tokens)
        every { artifactReplicaClient.replicaTemporaryTokenRequest(any()) } returns ok()

        buildReplicator().replicaTemporaryTokens(context)

        verify(exactly = 2) { artifactReplicaClient.replicaTemporaryTokenRequest(any()) }
    }

    @Test
    fun `replicaTemporaryTokens - should push correct token fields including permits and authorizedUserList`() {
        val token = buildTemporaryToken(
            "token-xyz", PROJECT_ID, REPO_NAME, "/secure/file.zip",
            permits = 5,
            authorizedUserList = setOf("alice", "bob")
        )
        every { localTemporaryTokenClient.listActiveByProject(PROJECT_ID) } returns ok(listOf(token))
        val slot = slot<TemporaryTokenReplicaRequest>()
        every { artifactReplicaClient.replicaTemporaryTokenRequest(capture(slot)) } returns ok()

        buildReplicator().replicaTemporaryTokens(context)

        assertEquals("token-xyz", slot.captured.token)
        assertEquals(PROJECT_ID, slot.captured.projectId)
        assertEquals(5, slot.captured.permits)
        assertTrue(slot.captured.authorizedUserList.contains("alice"))
    }

    @Test
    fun `replicaTemporaryTokens - empty list should not push`() {
        every { localTemporaryTokenClient.listActiveByProject(PROJECT_ID) } returns ok(emptyList())

        buildReplicator().replicaTemporaryTokens(context)

        verify(exactly = 0) { artifactReplicaClient.replicaTemporaryTokenRequest(any()) }
    }

    @Test
    fun `replicaTemporaryTokens - exception should be swallowed`() {
        every { localTemporaryTokenClient.listActiveByProject(PROJECT_ID) } throws
            RuntimeException("token service error")

        buildReplicator().replicaTemporaryTokens(context)

        verify(exactly = 0) { artifactReplicaClient.replicaTemporaryTokenRequest(any()) }
    }

    @Test
    fun `replicaTemporaryTokens - single token push failure should not abort remaining`() {
        val tokens = listOf(
            buildTemporaryToken("tok-ok", PROJECT_ID, REPO_NAME, "/ok.zip"),
            buildTemporaryToken("tok-fail", PROJECT_ID, REPO_NAME, "/fail.zip")
        )
        every { localTemporaryTokenClient.listActiveByProject(PROJECT_ID) } returns ok(tokens)
        every { artifactReplicaClient.replicaTemporaryTokenRequest(match { it.token == "tok-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaTemporaryTokenRequest(match { it.token == "tok-ok" }) } returns ok()

        buildReplicator().replicaTemporaryTokens(context)

        verify(exactly = 1) { artifactReplicaClient.replicaTemporaryTokenRequest(match { it.token == "tok-ok" }) }
    }

    // ==================== replicaOauthTokens ====================

    @Test
    fun `replicaOauthTokens - should fetch all active tokens and push each`() {
        val tokens = listOf(
            buildOauthTokenInfo("access-tok-1", "app-1", "alice"),
            buildOauthTokenInfo("access-tok-2", "app-2", "bob")
        )
        every { localOauthTokenClient.listActiveTokens() } returns ok(tokens)
        every { artifactReplicaClient.replicaOauthTokenRequest(any()) } returns ok()

        buildReplicator().replicaOauthTokens(context)

        verify(exactly = 2) { artifactReplicaClient.replicaOauthTokenRequest(any()) }
    }

    @Test
    fun `replicaOauthTokens - should push correct token fields`() {
        val token = buildOauthTokenInfo("access-tok-abc", "my-app", "carol")
        every { localOauthTokenClient.listActiveTokens() } returns ok(listOf(token))
        val slot = slot<OauthTokenReplicaRequest>()
        every { artifactReplicaClient.replicaOauthTokenRequest(capture(slot)) } returns ok()

        buildReplicator().replicaOauthTokens(context)

        assertEquals("access-tok-abc", slot.captured.accessToken)
        assertEquals("my-app", slot.captured.accountId)
        assertEquals("carol", slot.captured.userId)
        assertEquals("Bearer", slot.captured.type)
    }

    @Test
    fun `replicaOauthTokens - exception should be swallowed`() {
        every { localOauthTokenClient.listActiveTokens() } throws RuntimeException("oauth error")

        buildReplicator().replicaOauthTokens(context)

        verify(exactly = 0) { artifactReplicaClient.replicaOauthTokenRequest(any()) }
    }

    @Test
    fun `replicaOauthTokens - empty list should not push`() {
        every { localOauthTokenClient.listActiveTokens() } returns ok(emptyList())

        buildReplicator().replicaOauthTokens(context)

        verify(exactly = 0) { artifactReplicaClient.replicaOauthTokenRequest(any()) }
    }

    @Test
    fun `replicaOauthTokens - single token push failure should not abort remaining`() {
        val tokens = listOf(
            buildOauthTokenInfo("tok-ok", "app-1", "alice"),
            buildOauthTokenInfo("tok-fail", "app-2", "bob")
        )
        every { localOauthTokenClient.listActiveTokens() } returns ok(tokens)
        every { artifactReplicaClient.replicaOauthTokenRequest(match { it.accessToken == "tok-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaOauthTokenRequest(match { it.accessToken == "tok-ok" }) } returns ok()

        buildReplicator().replicaOauthTokens(context)

        verify(exactly = 1) { artifactReplicaClient.replicaOauthTokenRequest(match { it.accessToken == "tok-ok" }) }
    }

    // ==================== replicaPersonalPaths ====================

    @Test
    fun `replicaPersonalPaths - should fetch personal paths by project and push each`() {
        val paths = listOf(
            buildPersonalPath("alice", PROJECT_ID, REPO_NAME, "/personal/alice"),
            buildPersonalPath("bob", PROJECT_ID, REPO_NAME, "/personal/bob")
        )
        every { localPermissionClient.listPersonalPath(PROJECT_ID) } returns ok(paths)
        every { artifactReplicaClient.replicaPersonalPathRequest(any()) } returns ok()

        buildReplicator().replicaPersonalPaths(context)

        verify(exactly = 2) { artifactReplicaClient.replicaPersonalPathRequest(any()) }
    }

    @Test
    fun `replicaPersonalPaths - should push correct userId and path`() {
        val path = buildPersonalPath("dave", PROJECT_ID, REPO_NAME, "/personal/dave/workspace")
        every { localPermissionClient.listPersonalPath(PROJECT_ID) } returns ok(listOf(path))
        val slot = slot<PersonalPathReplicaRequest>()
        every { artifactReplicaClient.replicaPersonalPathRequest(capture(slot)) } returns ok()

        buildReplicator().replicaPersonalPaths(context)

        assertEquals("dave", slot.captured.userId)
        assertEquals(PROJECT_ID, slot.captured.projectId)
        assertEquals(REPO_NAME, slot.captured.repoName)
        assertEquals("/personal/dave/workspace", slot.captured.fullPath)
    }

    @Test
    fun `replicaPersonalPaths - exception should be swallowed`() {
        every { localPermissionClient.listPersonalPath(PROJECT_ID) } throws RuntimeException("db error")

        buildReplicator().replicaPersonalPaths(context)

        verify(exactly = 0) { artifactReplicaClient.replicaPersonalPathRequest(any()) }
    }

    @Test
    fun `replicaPersonalPaths - empty list should not push`() {
        every { localPermissionClient.listPersonalPath(PROJECT_ID) } returns ok(emptyList())

        buildReplicator().replicaPersonalPaths(context)

        verify(exactly = 0) { artifactReplicaClient.replicaPersonalPathRequest(any()) }
    }

    @Test
    fun `replicaPersonalPaths - single push failure should not abort remaining`() {
        val paths = listOf(
            buildPersonalPath("alice", PROJECT_ID, REPO_NAME, "/personal/alice"),
            buildPersonalPath("bob", PROJECT_ID, REPO_NAME, "/personal/bob")
        )
        every { localPermissionClient.listPersonalPath(PROJECT_ID) } returns ok(paths)
        every { artifactReplicaClient.replicaPersonalPathRequest(match { it.userId == "bob" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaPersonalPathRequest(match { it.userId == "alice" }) } returns ok()

        buildReplicator().replicaPersonalPaths(context)

        verify(exactly = 1) { artifactReplicaClient.replicaPersonalPathRequest(match { it.userId == "alice" }) }
    }

    // ==================== replicaProxies ====================

    @Test
    fun `replicaProxies - should fetch proxies by project and push each`() {
        val proxies = listOf(buildProxyInfo("proxy-1", PROJECT_ID), buildProxyInfo("proxy-2", PROJECT_ID))
        every { localProxyClient.listProxyByProject(PROJECT_ID) } returns ok(proxies)
        every { artifactReplicaClient.replicaProxyRequest(any()) } returns ok()

        buildReplicator().replicaProxies(context)

        verify(exactly = 2) { artifactReplicaClient.replicaProxyRequest(any()) }
    }

    @Test
    fun `replicaProxies - should push static config fields but NOT sensitive runtime fields`() {
        val proxy = buildProxyInfo("edge-proxy", PROJECT_ID, domain = "proxy.example.com", syncRateLimit = 1024 * 1024)
        every { localProxyClient.listProxyByProject(PROJECT_ID) } returns ok(listOf(proxy))
        val slot = slot<ProxyReplicaRequest>()
        every { artifactReplicaClient.replicaProxyRequest(capture(slot)) } returns ok()

        buildReplicator().replicaProxies(context)

        assertEquals("edge-proxy", slot.captured.name)
        assertEquals(PROJECT_ID, slot.captured.projectId)
        assertEquals("proxy.example.com", slot.captured.domain)
        assertEquals(1024 * 1024L, slot.captured.syncRateLimit)
        // ip / status / secretKey are NOT included in ProxyReplicaRequest
    }

    @Test
    fun `replicaProxies - empty list should not push`() {
        every { localProxyClient.listProxyByProject(PROJECT_ID) } returns ok(emptyList())

        buildReplicator().replicaProxies(context)

        verify(exactly = 0) { artifactReplicaClient.replicaProxyRequest(any()) }
    }

    @Test
    fun `replicaProxies - exception should be swallowed`() {
        every { localProxyClient.listProxyByProject(PROJECT_ID) } throws RuntimeException("proxy service error")

        buildReplicator().replicaProxies(context)

        verify(exactly = 0) { artifactReplicaClient.replicaProxyRequest(any()) }
    }

    @Test
    fun `replicaProxies - single push failure should not abort remaining`() {
        val proxies = listOf(buildProxyInfo("proxy-ok", PROJECT_ID), buildProxyInfo("proxy-fail", PROJECT_ID))
        every { localProxyClient.listProxyByProject(PROJECT_ID) } returns ok(proxies)
        every { artifactReplicaClient.replicaProxyRequest(match { it.name == "proxy-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaProxyRequest(match { it.name == "proxy-ok" }) } returns ok()

        buildReplicator().replicaProxies(context)

        verify(exactly = 1) { artifactReplicaClient.replicaProxyRequest(match { it.name == "proxy-ok" }) }
    }

    // ==================== helper builders ====================

    private fun <T> ok(data: T? = null) = Response(CommonMessageCode.SUCCESS.getCode(), data = data)

    private fun buildReplicator() = FederationReplicatorNewEntitiesTestHelper(
        localRoleClient = localRoleClient,
        localAccountClient = localAccountClient,
        localExternalPermissionClient = localExternalPermissionClient,
        localTemporaryTokenClient = localTemporaryTokenClient,
        localOauthTokenClient = localOauthTokenClient,
        localPermissionClient = localPermissionClient,
        localProxyClient = localProxyClient,
        localKeyClient = localKeyClient,
        localRepoModeClient = localRepoModeClient,
        localUserClient = localUserClient
    )

    private fun buildRoleInfo(
        roleId: String,
        type: String,
        admin: Boolean = false,
        description: String? = null
    ) = RoleInfo(
        id = "id-$roleId",
        roleId = roleId,
        name = "name-$roleId",
        type = type,
        projectId = PROJECT_ID,
        admin = admin,
        description = description
    )

    private fun buildAccountInfo(appId: String, locked: Boolean = false) = AccountInfo(
        id = "id-$appId",
        appId = appId,
        locked = locked,
        authorizationGrantTypes = setOf("PLATFORM"),
        description = "test app"
    )

    private fun buildExternalPermission(
        projectId: String,
        repoName: String,
        url: String = "https://ext.example.com",
        headers: Map<String, String> = emptyMap(),
        enabled: Boolean = true
    ) = ExternalPermission(
        id = "id-$projectId-$repoName",
        url = url,
        headers = headers,
        projectId = projectId,
        repoName = repoName,
        scope = "generic",
        platformWhiteList = emptyList(),
        enabled = enabled,
        createdDate = LocalDateTime.now(),
        createdBy = "admin",
        lastModifiedDate = LocalDateTime.now(),
        lastModifiedBy = "admin"
    )

    private fun buildTemporaryToken(
        token: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        permits: Int? = null,
        authorizedUserList: Set<String> = emptySet()
    ) = TemporaryTokenInfo(
        projectId = projectId,
        repoName = repoName,
        fullPath = fullPath,
        token = token,
        authorizedUserList = authorizedUserList,
        authorizedIpList = emptySet(),
        expireDate = null,
        permits = permits,
        type = TokenType.DOWNLOAD,
        createdBy = "admin"
    )

    private fun buildOauthTokenInfo(
        accessToken: String,
        accountId: String,
        userId: String
    ) = OauthTokenInfo(
        accessToken = accessToken,
        type = "Bearer",
        accountId = accountId,
        userId = userId,
        issuedAt = Instant.now().epochSecond
    )

    private fun buildPersonalPath(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String
    ) = PersonalPathInfo(
        userId = userId,
        projectId = projectId,
        repoName = repoName,
        fullPath = fullPath
    )

    private fun buildProxyInfo(
        name: String,
        projectId: String,
        domain: String = "proxy-$name.example.com",
        syncRateLimit: Long = 0L
    ) = ProxyInfo(
        name = name,
        displayName = name,
        projectId = projectId,
        clusterName = "cluster-a",
        domain = domain,
        ip = "192.168.1.1",
        status = ProxyStatus.ONLINE,
        syncRateLimit = syncRateLimit,
        syncTimeRange = "00:00-23:59",
        cacheExpireDays = 7
    )

    private fun buildKeyInfo(userId: String, fingerprint: String = "ab:cd:ef:01") = KeyInfo(
        id = "key-id-$fingerprint",
        name = "key-$fingerprint",
        key = "ssh-rsa AAAAB3NzaC1yc2EA $userId@host",
        fingerprint = fingerprint,
        userId = userId,
        createAt = java.time.LocalDateTime.now()
    )

    private fun buildRepoAuthConfigInfo(projectId: String, repoName: String) = RepoAuthConfigInfo(
        id = "cfg-$projectId-$repoName",
        projectId = projectId,
        repoName = repoName,
        accessControlMode = AccessControlMode.DEFAULT,
        officeDenyGroupSet = emptySet(),
        bkiamv3Check = false
    )

    // ==================== replicaKeys ====================

    @Test
    fun `replicaKeys - should fetch all users then keys per user and push each`() {
        val user1 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        every { user1.userId } returns "alice"
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1))
        every { localKeyClient.listKeyByUserId("alice") } returns ok(
            listOf(buildKeyInfo("alice", "fp-1"), buildKeyInfo("alice", "fp-2"))
        )
        every { artifactReplicaClient.replicaKeyRequest(any()) } returns ok()

        buildReplicator().replicaKeys(context)

        verify(exactly = 2) { artifactReplicaClient.replicaKeyRequest(any()) }
    }

    @Test
    fun `replicaKeys - should push correct key fields`() {
        val user1 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        every { user1.userId } returns "bob"
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1))
        val keyInfo = buildKeyInfo("bob", "11:22:33:44")
        every { localKeyClient.listKeyByUserId("bob") } returns ok(listOf(keyInfo))
        val slot = slot<KeyReplicaRequest>()
        every { artifactReplicaClient.replicaKeyRequest(capture(slot)) } returns ok()

        buildReplicator().replicaKeys(context)

        assertEquals("11:22:33:44", slot.captured.fingerprint)
        assertEquals("bob", slot.captured.userId)
    }

    @Test
    fun `replicaKeys - listUser exception should be swallowed`() {
        every { localUserClient.listUser(emptyList(), null) } throws RuntimeException("auth down")

        buildReplicator().replicaKeys(context)

        verify(exactly = 0) { artifactReplicaClient.replicaKeyRequest(any()) }
    }

    @Test
    fun `replicaKeys - user with no keys should not push`() {
        val user1 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        every { user1.userId } returns "carol"
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1))
        every { localKeyClient.listKeyByUserId("carol") } returns ok(emptyList())

        buildReplicator().replicaKeys(context)

        verify(exactly = 0) { artifactReplicaClient.replicaKeyRequest(any()) }
    }

    @Test
    fun `replicaKeys - listKeyByUserId exception for one user should not abort remaining users`() {
        val user1 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        val user2 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        every { user1.userId } returns "alice"
        every { user2.userId } returns "bob"
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1, user2))
        every { localKeyClient.listKeyByUserId("alice") } throws RuntimeException("auth error")
        every { localKeyClient.listKeyByUserId("bob") } returns ok(listOf(buildKeyInfo("bob", "bb:cc")))
        every { artifactReplicaClient.replicaKeyRequest(any()) } returns ok()

        buildReplicator().replicaKeys(context)

        // alice's keys fetch failed, only bob's key should be pushed
        verify(exactly = 1) { artifactReplicaClient.replicaKeyRequest(any()) }
    }

    @Test
    fun `replicaKeys - single key push failure should not abort remaining keys`() {
        val user1 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        every { user1.userId } returns "dave"
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1))
        every { localKeyClient.listKeyByUserId("dave") } returns ok(
            listOf(buildKeyInfo("dave", "fp-ok"), buildKeyInfo("dave", "fp-fail"))
        )
        every { artifactReplicaClient.replicaKeyRequest(match { it.fingerprint == "fp-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaKeyRequest(match { it.fingerprint == "fp-ok" }) } returns ok()

        buildReplicator().replicaKeys(context)

        verify(exactly = 1) { artifactReplicaClient.replicaKeyRequest(match { it.fingerprint == "fp-ok" }) }
    }

    @Test
    fun `replicaKeys - multiple users each with keys should push all keys`() {
        val user1 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        val user2 = mockk<com.tencent.bkrepo.auth.pojo.user.User>(relaxed = true)
        every { user1.userId } returns "alice"
        every { user2.userId } returns "bob"
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1, user2))
        every { localKeyClient.listKeyByUserId("alice") } returns ok(
            listOf(buildKeyInfo("alice", "aa:bb"), buildKeyInfo("alice", "cc:dd"))
        )
        every { localKeyClient.listKeyByUserId("bob") } returns ok(
            listOf(buildKeyInfo("bob", "ee:ff"))
        )
        every { artifactReplicaClient.replicaKeyRequest(any()) } returns ok()

        buildReplicator().replicaKeys(context)

        verify(exactly = 3) { artifactReplicaClient.replicaKeyRequest(any()) }
    }

    // ==================== replicaRepoAuthConfig ====================

    @Test
    fun `replicaRepoAuthConfig - should fetch configs by project and push each`() {
        val configs = listOf(
            buildRepoAuthConfigInfo(PROJECT_ID, "repo-1"),
            buildRepoAuthConfigInfo(PROJECT_ID, "repo-2")
        )
        every { localRepoModeClient.listByProject(PROJECT_ID) } returns ok(configs)
        every { artifactReplicaClient.replicaRepoAuthConfigRequest(any()) } returns ok()

        buildReplicator().replicaRepoAuthConfig(context)

        verify(exactly = 2) { artifactReplicaClient.replicaRepoAuthConfigRequest(any()) }
    }

    @Test
    fun `replicaRepoAuthConfig - should push correct fields`() {
        val config = buildRepoAuthConfigInfo(PROJECT_ID, REPO_NAME).copy(
            accessControlMode = AccessControlMode.STRICT,
            bkiamv3Check = true
        )
        every { localRepoModeClient.listByProject(PROJECT_ID) } returns ok(listOf(config))
        val slot = slot<RepoAuthConfigReplicaRequest>()
        every { artifactReplicaClient.replicaRepoAuthConfigRequest(capture(slot)) } returns ok()

        buildReplicator().replicaRepoAuthConfig(context)

        assertEquals(PROJECT_ID, slot.captured.projectId)
        assertEquals(REPO_NAME, slot.captured.repoName)
        assertEquals("STRICT", slot.captured.accessControlMode)
        assertTrue(slot.captured.bkiamv3Check)
    }

    @Test
    fun `replicaRepoAuthConfig - empty list should not push`() {
        every { localRepoModeClient.listByProject(PROJECT_ID) } returns ok(emptyList())

        buildReplicator().replicaRepoAuthConfig(context)

        verify(exactly = 0) { artifactReplicaClient.replicaRepoAuthConfigRequest(any()) }
    }

    @Test
    fun `replicaRepoAuthConfig - exception should be swallowed`() {
        every { localRepoModeClient.listByProject(PROJECT_ID) } throws RuntimeException("db error")

        buildReplicator().replicaRepoAuthConfig(context)

        verify(exactly = 0) { artifactReplicaClient.replicaRepoAuthConfigRequest(any()) }
    }

    @Test
    fun `replicaRepoAuthConfig - single push failure should not abort remaining configs`() {
        val configs = listOf(
            buildRepoAuthConfigInfo(PROJECT_ID, "repo-ok"),
            buildRepoAuthConfigInfo(PROJECT_ID, "repo-fail")
        )
        every { localRepoModeClient.listByProject(PROJECT_ID) } returns ok(configs)
        every { artifactReplicaClient.replicaRepoAuthConfigRequest(match { it.repoName == "repo-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaRepoAuthConfigRequest(match { it.repoName == "repo-ok" }) } returns ok()

        buildReplicator().replicaRepoAuthConfig(context)

        verify(exactly = 1) {
            artifactReplicaClient.replicaRepoAuthConfigRequest(match { it.repoName == "repo-ok" })
        }
    }

    // ==================== replicaUsers ====================

    @Test
    fun `replicaUsers - should list all users and push each to target`() {
        val user1 = buildUser("alice")
        val user2 = buildUser("bob")
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1, user2))
        every { localUserClient.userInfoById("alice") } returns ok(buildUserInfo("alice"))
        every { localUserClient.userInfoById("bob") } returns ok(buildUserInfo("bob"))
        every { artifactReplicaClient.replicaUserRequest(any()) } returns ok()

        buildReplicator().replicaUsers(context)

        verify(exactly = 2) { artifactReplicaClient.replicaUserRequest(any()) }
    }

    @Test
    fun `replicaUsers - should push phone and tenantId from UserInfo`() {
        val user = buildUser("carol")
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user))
        every { localUserClient.userInfoById("carol") } returns ok(
            buildUserInfo("carol", phone = "13900139000", tenantId = "tenant-x")
        )
        val slot = slot<UserReplicaRequest>()
        every { artifactReplicaClient.replicaUserRequest(capture(slot)) } returns ok()

        buildReplicator().replicaUsers(context)

        assertEquals("carol", slot.captured.userId)
        assertEquals("13900139000", slot.captured.phone)
        assertEquals("tenant-x", slot.captured.tenantId)
    }

    @Test
    fun `replicaUsers - userInfoById failure should fallback to User fields`() {
        val user = buildUser("dave", admin = true)
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user))
        every { localUserClient.userInfoById("dave") } throws RuntimeException("auth service down")
        val slot = slot<UserReplicaRequest>()
        every { artifactReplicaClient.replicaUserRequest(capture(slot)) } returns ok()

        buildReplicator().replicaUsers(context)

        verify(exactly = 1) { artifactReplicaClient.replicaUserRequest(any()) }
        assertEquals("dave", slot.captured.userId)
        assertEquals(true, slot.captured.admin)
    }

    @Test
    fun `replicaUsers - empty user list should not push`() {
        every { localUserClient.listUser(emptyList(), null) } returns ok(emptyList())

        buildReplicator().replicaUsers(context)

        verify(exactly = 0) { artifactReplicaClient.replicaUserRequest(any()) }
    }

    @Test
    fun `replicaUsers - listUser exception should be swallowed gracefully`() {
        every { localUserClient.listUser(emptyList(), null) } throws RuntimeException("network error")

        buildReplicator().replicaUsers(context)

        verify(exactly = 0) { artifactReplicaClient.replicaUserRequest(any()) }
    }

    @Test
    fun `replicaUsers - single user push failure should not abort remaining users`() {
        val user1 = buildUser("user-ok")
        val user2 = buildUser("user-fail")
        every { localUserClient.listUser(emptyList(), null) } returns ok(listOf(user1, user2))
        every { localUserClient.userInfoById("user-ok") } returns ok(buildUserInfo("user-ok"))
        every { localUserClient.userInfoById("user-fail") } returns ok(buildUserInfo("user-fail"))
        every { artifactReplicaClient.replicaUserRequest(match { it.userId == "user-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaUserRequest(match { it.userId == "user-ok" }) } returns ok()

        buildReplicator().replicaUsers(context)

        verify(exactly = 1) { artifactReplicaClient.replicaUserRequest(match { it.userId == "user-ok" }) }
    }

    // ==================== replicaPermissions ====================

    @Test
    fun `replicaPermissions - should list PROJECT and REPO permissions and push all`() {
        every { localPermissionClient.listPermission(PROJECT_ID, null, "PROJECT") } returns ok(
            listOf(buildPermission("perm-proj-1", "PROJECT"), buildPermission("perm-proj-2", "PROJECT"))
        )
        every { localPermissionClient.listPermission(PROJECT_ID, null, "REPO") } returns ok(
            listOf(buildPermission("perm-repo-1", "REPO"))
        )
        every { artifactReplicaClient.replicaPermissionRequest(any()) } returns ok()

        buildReplicator().replicaPermissions(context)

        verify(exactly = 3) { artifactReplicaClient.replicaPermissionRequest(any()) }
    }

    @Test
    fun `replicaPermissions - should push correct permission fields`() {
        every { localPermissionClient.listPermission(PROJECT_ID, null, "PROJECT") } returns ok(
            listOf(buildPermission("mgr-perm", "PROJECT", users = listOf("alice"), actions = listOf("READ", "WRITE")))
        )
        every { localPermissionClient.listPermission(PROJECT_ID, null, "REPO") } returns ok(emptyList())
        val slot = slot<PermissionReplicaRequest>()
        every { artifactReplicaClient.replicaPermissionRequest(capture(slot)) } returns ok()

        buildReplicator().replicaPermissions(context)

        assertEquals("mgr-perm", slot.captured.permName)
        assertEquals("PROJECT", slot.captured.resourceType)
        assertEquals(PROJECT_ID, slot.captured.projectId)
        assertEquals(listOf("alice"), slot.captured.users)
        assertEquals(listOf("READ", "WRITE"), slot.captured.actions)
    }

    @Test
    fun `replicaPermissions - empty both lists should not push`() {
        every { localPermissionClient.listPermission(PROJECT_ID, null, "PROJECT") } returns ok(emptyList())
        every { localPermissionClient.listPermission(PROJECT_ID, null, "REPO") } returns ok(emptyList())

        buildReplicator().replicaPermissions(context)

        verify(exactly = 0) { artifactReplicaClient.replicaPermissionRequest(any()) }
    }

    @Test
    fun `replicaPermissions - listPermission exception should be swallowed gracefully`() {
        every { localPermissionClient.listPermission(PROJECT_ID, null, "PROJECT") } throws RuntimeException("auth down")
        every { localPermissionClient.listPermission(PROJECT_ID, null, "REPO") } throws RuntimeException("auth down")

        buildReplicator().replicaPermissions(context)

        verify(exactly = 0) { artifactReplicaClient.replicaPermissionRequest(any()) }
    }

    @Test
    fun `replicaPermissions - single push failure should not abort remaining permissions`() {
        every { localPermissionClient.listPermission(PROJECT_ID, null, "PROJECT") } returns ok(
            listOf(buildPermission("perm-ok", "PROJECT"), buildPermission("perm-fail", "PROJECT"))
        )
        every { localPermissionClient.listPermission(PROJECT_ID, null, "REPO") } returns ok(emptyList())
        every { artifactReplicaClient.replicaPermissionRequest(match { it.permName == "perm-fail" }) } throws
            RuntimeException("push failed")
        every { artifactReplicaClient.replicaPermissionRequest(match { it.permName == "perm-ok" }) } returns ok()

        buildReplicator().replicaPermissions(context)

        verify(exactly = 1) { artifactReplicaClient.replicaPermissionRequest(match { it.permName == "perm-ok" }) }
    }

    companion object {
        const val PROJECT_ID = "test-project"
        const val REPO_NAME = "test-repo"
    }

    private fun buildUser(userId: String, admin: Boolean = false) = com.tencent.bkrepo.auth.pojo.user.User(
        userId = userId,
        name = "name-$userId",
        admin = admin
    )

    private fun buildUserInfo(
        userId: String,
        phone: String? = null,
        tenantId: String? = null
    ) = UserInfo(
        userId = userId,
        name = "name-$userId",
        email = null,
        phone = phone,
        createdDate = null,
        locked = false,
        admin = false,
        group = false,
        tenantId = tenantId
    )

    private fun buildPermission(
        permName: String,
        resourceType: String,
        users: List<String> = emptyList(),
        actions: List<String> = emptyList()
    ) = Permission(
        id = "id-$permName",
        resourceType = resourceType,
        projectId = PROJECT_ID,
        permName = permName,
        users = users,
        actions = actions,
        createBy = "admin",
        updatedBy = "admin",
        createAt = LocalDateTime.now(),
        updateAt = LocalDateTime.now()
    )
}

/**
 * 测试辅助类，封装与真实 FederationReplicator 对齐的源端同步逻辑。
 * 使用真实 API：listRoleByProject / listAccountsForFederation / listActiveByProject /
 * listActiveTokens / listPersonalPath(projectId) / listProxyByProject /
 * listKeyByUserId(userId) / listByProject(projectId)
 */
class FederationReplicatorNewEntitiesTestHelper(
    val localRoleClient: ServiceRoleClient,
    val localAccountClient: ServiceAccountClient,
    val localExternalPermissionClient: ServiceExternalPermissionClient,
    val localTemporaryTokenClient: ServiceTemporaryTokenClient,
    val localOauthTokenClient: ServiceOauthAuthorizationClient,
    val localPermissionClient: ServicePermissionClient,
    val localProxyClient: ServiceProxyClient,
    val localKeyClient: ServiceKeyClient,
    val localRepoModeClient: ServiceRepoModeClient,
    val localUserClient: ServiceUserClient
) {
    fun replicaRoles(context: ReplicaContext) = safe {
        val roles = localRoleClient.listRoleByProject(context.localProjectId).data ?: return@safe
        roles.forEach { role ->
            try {
                context.artifactReplicaClient!!.replicaRoleRequest(
                    RoleReplicaRequest(
                        id = role.id,
                        roleId = role.roleId,
                        name = role.name,
                        type = role.type,
                        projectId = role.projectId,
                        repoName = role.repoName,
                        admin = role.admin,
                        users = role.users,
                        description = role.description
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaAccounts(context: ReplicaContext) = safe {
        val accounts = localAccountClient.listAccountsForFederation().data ?: return@safe
        accounts.forEach { acc ->
            try {
                context.artifactReplicaClient!!.replicaAccountRequest(
                    AccountReplicaRequest(
                        appId = acc.appId,
                        locked = acc.locked,
                        authorizationGrantTypes = acc.authorizationGrantTypes,
                        homepageUrl = acc.homepageUrl,
                        redirectUri = acc.redirectUri,
                        avatarUrl = acc.avatarUrl,
                        scope = acc.scope,
                        description = acc.description
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaExternalPermissions(context: ReplicaContext) = safe {
        val perms = localExternalPermissionClient.listExternalPermission().data ?: return@safe
        perms.forEach { perm ->
            try {
                context.artifactReplicaClient!!.replicaExternalPermissionRequest(
                    ExternalPermissionReplicaRequest(
                        id = perm.id,
                        url = perm.url,
                        headers = perm.headers,
                        projectId = perm.projectId,
                        repoName = perm.repoName,
                        scope = perm.scope,
                        platformWhiteList = perm.platformWhiteList,
                        enabled = perm.enabled
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaTemporaryTokens(context: ReplicaContext) = safe {
        val tokens = localTemporaryTokenClient.listActiveByProject(context.localProjectId).data ?: return@safe
        tokens.forEach { token ->
            try {
                context.artifactReplicaClient!!.replicaTemporaryTokenRequest(
                    TemporaryTokenReplicaRequest(
                        projectId = token.projectId,
                        repoName = token.repoName,
                        fullPath = token.fullPath,
                        token = token.token,
                        authorizedUserList = token.authorizedUserList,
                        authorizedIpList = token.authorizedIpList,
                        expireDate = token.expireDate,
                        permits = token.permits,
                        type = token.type.name,
                        createdBy = token.createdBy
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaOauthTokens(context: ReplicaContext) = safe {
        val tokens = localOauthTokenClient.listActiveTokens().data ?: return@safe
        tokens.forEach { token ->
            try {
                context.artifactReplicaClient!!.replicaOauthTokenRequest(
                    OauthTokenReplicaRequest(
                        accessToken = token.accessToken,
                        refreshToken = token.refreshToken,
                        expireSeconds = token.expireSeconds,
                        type = token.type,
                        accountId = token.accountId,
                        userId = token.userId,
                        scope = token.scope,
                        issuedAt = token.issuedAt
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaPersonalPaths(context: ReplicaContext) = safe {
        val paths = localPermissionClient.listPersonalPath(context.localProjectId).data ?: return@safe
        paths.forEach { path ->
            try {
                context.artifactReplicaClient!!.replicaPersonalPathRequest(
                    PersonalPathReplicaRequest(
                        userId = path.userId,
                        projectId = path.projectId,
                        repoName = path.repoName,
                        fullPath = path.fullPath
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaProxies(context: ReplicaContext) = safe {
        val proxies = localProxyClient.listProxyByProject(context.localProjectId).data ?: return@safe
        proxies.forEach { proxy ->
            try {
                context.artifactReplicaClient!!.replicaProxyRequest(
                    ProxyReplicaRequest(
                        name = proxy.name,
                        displayName = proxy.displayName,
                        projectId = proxy.projectId,
                        clusterName = proxy.clusterName,
                        domain = proxy.domain,
                        syncRateLimit = proxy.syncRateLimit,
                        syncTimeRange = proxy.syncTimeRange,
                        cacheExpireDays = proxy.cacheExpireDays
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaKeys(context: ReplicaContext) = safe {
        val users = localUserClient.listUser(emptyList(), null).data ?: return@safe
        users.forEach { user ->
            val keys = try {
                localKeyClient.listKeyByUserId(user.userId).data ?: return@forEach
            } catch (_: Exception) { return@forEach }
            keys.forEach { keyInfo ->
                try {
                    context.artifactReplicaClient!!.replicaKeyRequest(
                        KeyReplicaRequest(
                            id = keyInfo.id,
                            name = keyInfo.name,
                            key = keyInfo.key,
                            fingerprint = keyInfo.fingerprint,
                            userId = keyInfo.userId,
                            createAt = keyInfo.createAt.toString()
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun replicaRepoAuthConfig(context: ReplicaContext) = safe {
        val configs = localRepoModeClient.listByProject(context.localProjectId).data ?: return@safe
        configs.forEach { config ->
            try {
                context.artifactReplicaClient!!.replicaRepoAuthConfigRequest(
                    RepoAuthConfigReplicaRequest(
                        id = config.id,
                        projectId = config.projectId,
                        repoName = config.repoName,
                        accessControlMode = config.accessControlMode?.name ?: "DEFAULT",
                        officeDenyGroupSet = config.officeDenyGroupSet,
                        bkiamv3Check = config.bkiamv3Check
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaUsers(context: ReplicaContext) = safe {
        val users = localUserClient.listUser(emptyList(), null).data ?: return@safe
        users.forEach { user ->
            try {
                val userInfo = try {
                    localUserClient.userInfoById(user.userId).data
                } catch (_: Exception) { null }
                context.artifactReplicaClient!!.replicaUserRequest(
                    UserReplicaRequest(
                        userId = user.userId,
                        name = userInfo?.name ?: user.name,
                        pwd = null,
                        admin = userInfo?.admin ?: user.admin,
                        asstUsers = userInfo?.asstUsers ?: user.asstUsers,
                        group = userInfo?.group ?: user.group,
                        email = userInfo?.email ?: user.email,
                        phone = userInfo?.phone,
                        tenantId = userInfo?.tenantId
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun replicaPermissions(context: ReplicaContext) = safe {
        val permissions = try {
            val projectPerms = localPermissionClient.listPermission(
                context.localProjectId, null, "PROJECT"
            ).data ?: emptyList()
            val repoPerms = localPermissionClient.listPermission(
                context.localProjectId, null, "REPO"
            ).data ?: emptyList()
            projectPerms + repoPerms
        } catch (_: Exception) { return@safe }
        permissions.forEach { perm ->
            try {
                context.artifactReplicaClient!!.replicaPermissionRequest(
                    PermissionReplicaRequest(
                        resourceType = perm.resourceType,
                        projectId = perm.projectId,
                        permName = perm.permName,
                        repos = perm.repos,
                        includePattern = perm.includePattern,
                        excludePattern = perm.excludePattern,
                        users = perm.users,
                        roles = perm.roles,
                        departments = perm.departments,
                        actions = perm.actions,
                        createBy = perm.createBy,
                        updatedBy = perm.updatedBy
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun safe(block: () -> Unit) {
        try { block() } catch (_: Exception) {}
    }
}
