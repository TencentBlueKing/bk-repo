package com.tencent.bkrepo.replication.controller.service

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.context.FederationReplicaContext
import com.tencent.bkrepo.replication.pojo.request.PermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaAction
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.common.service.util.SpringContextUtils
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class ArtifactReplicaControllerAuthTest {

    @MockK
    private lateinit var userResource: ServiceUserClient

    @MockK
    private lateinit var localPermissionClient: ServicePermissionClient

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
            localRoleClient = mockk(relaxed = true),
            localAccountClient = mockk(relaxed = true),
            localExternalPermissionClient = mockk(relaxed = true),
            localTemporaryTokenClient = mockk(relaxed = true),
            localOauthAuthorizationClient = mockk(relaxed = true),
            localProxyClient = mockk(relaxed = true),
            localKeyClient = mockk(relaxed = true),
            localRepoModeClient = mockk(relaxed = true),
        )
    }

    @AfterEach
    fun tearDown() {
        FederationReplicaContext.clear()
        unmockkObject(SpringContextUtils)
    }

    // ==================== replicaUserRequest ====================

    @Test
    fun `replicaUserRequest UPSERT - should call upsertUserForFederation`() {
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "new-user",
            name = "New User",
            admin = false,
            email = "new@example.com"
        )
        every { userResource.upsertUserForFederation(any(), any()) } returns ok()

        controller.replicaUserRequest(request)

        verify(exactly = 1) { userResource.upsertUserForFederation(any(), any()) }
    }

    @Test
    fun `replicaUserRequest UPSERT - admin and email passed to upsertUserForFederation`() {
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "user-a",
            name = "User A",
            admin = true,
            email = "a@example.com"
        )
        val createSlot = slot<CreateUserRequest>()
        every { userResource.upsertUserForFederation(capture(createSlot), any()) } returns ok()

        controller.replicaUserRequest(request)

        assertEquals(true, createSlot.captured.admin)
        assertEquals("a@example.com", createSlot.captured.email)
    }

    @Test
    fun `replicaUserRequest UPSERT - phone and tenantId passed to upsertUserForFederation`() {
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "user-t",
            name = "User T",
            phone = "13900139000",
            tenantId = "tenant-b"
        )
        val createSlot = slot<CreateUserRequest>()
        every { userResource.upsertUserForFederation(capture(createSlot), any()) } returns ok()

        controller.replicaUserRequest(request)

        assertEquals("13900139000", createSlot.captured.phone)
        assertEquals("tenant-b", createSlot.captured.tenantId)
    }

    @Test
    fun `replicaUserRequest UPSERT - pwd forwarded as hashedPwd`() {
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "user-p",
            pwd = "hashed-secret"
        )
        val pwdSlot = slot<String?>()
        every { userResource.upsertUserForFederation(any(), captureNullable(pwdSlot)) } returns ok()

        controller.replicaUserRequest(request)

        assertEquals("hashed-secret", pwdSlot.captured)
    }

    @Test
    fun `replicaUserRequest DELETE - should delete user`() {
        val request = UserReplicaRequest(
            action = ReplicaAction.DELETE,
            userId = "to-delete"
        )
        every { userResource.deleteUser("to-delete") } returns ok(true)

        controller.replicaUserRequest(request)

        verify(exactly = 1) { userResource.deleteUser("to-delete") }
        verify(exactly = 0) { userResource.upsertUserForFederation(any(), any()) }
    }

    @Test
    fun `replicaUserRequest - FederationReplicaContext should be cleared after execution`() {
        val request = UserReplicaRequest(action = ReplicaAction.UPSERT, userId = "u1")
        every { userResource.upsertUserForFederation(any(), any()) } returns ok()

        controller.replicaUserRequest(request)

        assertFalse(FederationReplicaContext.isFederationWrite())
    }

    @Test
    fun `replicaUserRequest - FederationReplicaContext cleared even when exception occurs`() {
        val request = UserReplicaRequest(action = ReplicaAction.UPSERT, userId = "u-err")
        every { userResource.upsertUserForFederation(any(), any()) } throws RuntimeException("db error")

        try {
            controller.replicaUserRequest(request)
        } catch (_: Exception) {
        }

        assertFalse(FederationReplicaContext.isFederationWrite())
    }

    // ==================== replicaPermissionRequest ====================

    @Test
    fun `replicaPermissionRequest UPSERT - should call upsertPermissionForFederation`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "PROJECT",
            projectId = "proj-1",
            permName = "new-perm",
            actions = listOf("READ")
        )
        every { localPermissionClient.upsertPermissionForFederation(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 1) { localPermissionClient.upsertPermissionForFederation(any()) }
        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
        verify(exactly = 0) { localPermissionClient.createPermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest UPSERT - permission exists should atomically upsert without delete`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "PROJECT",
            projectId = "proj-1",
            permName = "exist-perm",
            users = listOf("alice", "bob"),
            actions = listOf("READ", "WRITE")
        )
        every { localPermissionClient.upsertPermissionForFederation(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 1) { localPermissionClient.upsertPermissionForFederation(any()) }
        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest DELETE - permission found should be deleted`() {
        val existing = buildPermission("del-perm", "proj-1", "REPO", id = "perm-id-del")
        val request = PermissionReplicaRequest(
            action = ReplicaAction.DELETE,
            resourceType = "REPO",
            projectId = "proj-1",
            permName = "del-perm"
        )
        every { localPermissionClient.listPermission("proj-1", null, "REPO") } returns ok(listOf(existing))
        every { localPermissionClient.deletePermission("perm-id-del") } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 1) { localPermissionClient.deletePermission("perm-id-del") }
        verify(exactly = 0) { localPermissionClient.createPermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest DELETE - permission not found should do nothing`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.DELETE,
            resourceType = "REPO",
            projectId = "proj-1",
            permName = "ghost-perm"
        )
        every { localPermissionClient.listPermission("proj-1", null, "REPO") } returns ok(emptyList())

        controller.replicaPermissionRequest(request)

        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest - null projectId UPSERT should call upsertPermissionForFederation`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "SYSTEM",
            projectId = null,
            permName = "sys-perm",
            actions = listOf("READ")
        )
        every { localPermissionClient.upsertPermissionForFederation(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 0) { localPermissionClient.listPermission(any(), any(), any()) }
        verify(exactly = 1) { localPermissionClient.upsertPermissionForFederation(any()) }
    }

    @Test
    fun `replicaPermissionRequest - null projectId DELETE with no existing perm should not delete`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.DELETE,
            resourceType = "SYSTEM",
            projectId = null,
            permName = "missing-sys-perm"
        )
        every { localPermissionClient.getPermissionByName(null, "SYSTEM", "missing-sys-perm") } returns ok(null)

        controller.replicaPermissionRequest(request)

        verify(exactly = 0) { localPermissionClient.listPermission(any(), any(), any()) }
        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest - null projectId DELETE with existing perm should delete`() {
        val existing = buildPermission("sys-perm", "proj-1", "SYSTEM", id = "sys-perm-id")
        val request = PermissionReplicaRequest(
            action = ReplicaAction.DELETE,
            resourceType = "SYSTEM",
            projectId = null,
            permName = "sys-perm"
        )
        every { localPermissionClient.getPermissionByName(null, "SYSTEM", "sys-perm") } returns ok(existing)
        every { localPermissionClient.deletePermission("sys-perm-id") } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 1) { localPermissionClient.deletePermission("sys-perm-id") }
        verify(exactly = 0) { localPermissionClient.createPermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest - FederationReplicaContext should be cleared after execution`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "PROJECT",
            projectId = "p",
            permName = "n"
        )
        every { localPermissionClient.upsertPermissionForFederation(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        assertFalse(FederationReplicaContext.isFederationWrite())
    }

    @Test
    fun `replicaPermissionRequest UPSERT - permission with null id should use atomic upsert`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "REPO",
            projectId = "proj-1",
            permName = "no-id-perm"
        )
        every { localPermissionClient.upsertPermissionForFederation(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
        verify(exactly = 1) { localPermissionClient.upsertPermissionForFederation(any()) }
    }

    // ==================== helper builders ====================

    private fun <T> ok(data: T? = null) = Response(CommonMessageCode.SUCCESS.getCode(), data = data)

    private fun buildPermission(
        permName: String,
        projectId: String,
        resourceType: String,
        id: String? = "id-$permName"
    ): Permission {
        return Permission(
            id = id,
            resourceType = resourceType,
            projectId = projectId,
            permName = permName,
            repos = emptyList(),
            includePattern = emptyList(),
            excludePattern = emptyList(),
            users = emptyList(),
            roles = emptyList(),
            departments = emptyList(),
            actions = emptyList(),
            createBy = "admin",
            updatedBy = "admin",
            createAt = LocalDateTime.now(),
            updateAt = LocalDateTime.now()
        )
    }
}
