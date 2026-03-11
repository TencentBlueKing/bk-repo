package com.tencent.bkrepo.replication.controller.service

import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.replication.context.FederationReplicaContext
import com.tencent.bkrepo.replication.manager.LocalDataManager
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
class ArtifactReplicaControllerAuthTest {    @MockK
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
    fun `replicaUserRequest UPSERT - user not exists should create user`() {
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "new-user",
            name = "New User",
            admin = false,
            email = "new@example.com"
        )
        every { userResource.userInfoById("new-user") } returns ok(null)
        every { userResource.createUser(any()) } returns ok(true)

        controller.replicaUserRequest(request)

        verify(exactly = 1) { userResource.createUser(any()) }
        verify(exactly = 0) { userResource.updateUserById(any(), any()) }
    }

    @Test
    fun `replicaUserRequest UPSERT - user exists should update user`() {
        val existingUser = buildUserInfo("exist-user", "Old Name", admin = false)
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "exist-user",
            name = "New Name",
            admin = true,
            email = "updated@example.com"
        )
        every { userResource.userInfoById("exist-user") } returns ok(existingUser)
        val updateSlot = slot<com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest>()
        every { userResource.updateUserById("exist-user", capture(updateSlot)) } returns ok(true)

        controller.replicaUserRequest(request)

        verify(exactly = 0) { userResource.createUser(any()) }
        verify(exactly = 1) { userResource.updateUserById("exist-user", any()) }
        assertEquals(true, updateSlot.captured.admin)
        assertEquals("updated@example.com", updateSlot.captured.email)
    }

    @Test
    fun `replicaUserRequest UPSERT - phone and tenantId passed to createUser`() {
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "user-t",
            name = "User T",
            phone = "13900139000",
            tenantId = "tenant-b"
        )
        every { userResource.userInfoById("user-t") } returns ok(null)
        val createSlot = slot<com.tencent.bkrepo.auth.pojo.user.CreateUserRequest>()
        every { userResource.createUser(capture(createSlot)) } returns ok(true)

        controller.replicaUserRequest(request)

        assertEquals("13900139000", createSlot.captured.phone)
        assertEquals("tenant-b", createSlot.captured.tenantId)
    }

    @Test
    fun `replicaUserRequest UPSERT - phone and tenantId passed to updateUserById`() {
        val existingUser = buildUserInfo("user-u", "User U", admin = false)
        val request = UserReplicaRequest(
            action = ReplicaAction.UPSERT,
            userId = "user-u",
            name = "User U",
            phone = "13700137000",
            tenantId = "tenant-c"
        )
        every { userResource.userInfoById("user-u") } returns ok(existingUser)
        val updateSlot = slot<com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest>()
        every { userResource.updateUserById("user-u", capture(updateSlot)) } returns ok(true)

        controller.replicaUserRequest(request)

        assertEquals("13700137000", updateSlot.captured.phone)
        assertEquals("tenant-c", updateSlot.captured.tenantId)
    }

    @Test
    fun `replicaPermissionRequest - null projectId should do nothing`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "REPO",
            projectId = null,
            permName = "perm-null-proj"
        )

        controller.replicaPermissionRequest(request)

        verify(exactly = 0) { localPermissionClient.listPermission(any(), any(), any()) }
        verify(exactly = 0) { localPermissionClient.createPermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest DELETE - null projectId should do nothing`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.DELETE,
            resourceType = "REPO",
            projectId = null,
            permName = "perm-null-del"
        )

        controller.replicaPermissionRequest(request)

        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
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
        verify(exactly = 0) { userResource.createUser(any()) }
        verify(exactly = 0) { userResource.updateUserById(any(), any()) }
    }

    @Test
    fun `replicaUserRequest - FederationReplicaContext should be cleared after execution`() {
        val request = UserReplicaRequest(action = ReplicaAction.UPSERT, userId = "u1")
        every { userResource.userInfoById("u1") } returns ok(null)
        every { userResource.createUser(any()) } returns ok(true)

        controller.replicaUserRequest(request)

        assertFalse(FederationReplicaContext.isFederationWrite())
    }

    @Test
    fun `replicaUserRequest - FederationReplicaContext cleared even when exception occurs`() {
        val request = UserReplicaRequest(action = ReplicaAction.UPSERT, userId = "u-err")
        every { userResource.userInfoById("u-err") } throws RuntimeException("db error")

        try {
            controller.replicaUserRequest(request)
        } catch (_: Exception) {}

        assertFalse(FederationReplicaContext.isFederationWrite())
    }

    // ==================== replicaPermissionRequest ====================

    @Test
    fun `replicaPermissionRequest UPSERT - permission not exists should create`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "PROJECT",
            projectId = "proj-1",
            permName = "new-perm",
            actions = listOf("READ")
        )
        every { localPermissionClient.listPermission("proj-1", null, "PROJECT") } returns ok(emptyList())
        every { localPermissionClient.createPermission(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 1) { localPermissionClient.createPermission(any()) }
        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
    }

    @Test
    fun `replicaPermissionRequest UPSERT - permission exists should delete then recreate`() {
        val existing = buildPermission("exist-perm", "proj-1", "PROJECT", id = "perm-id-123")
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "PROJECT",
            projectId = "proj-1",
            permName = "exist-perm",
            users = listOf("alice", "bob"),
            actions = listOf("READ", "WRITE")
        )
        every { localPermissionClient.listPermission("proj-1", null, "PROJECT") } returns ok(listOf(existing))
        every { localPermissionClient.deletePermission("perm-id-123") } returns ok(true)
        every { localPermissionClient.createPermission(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 1) { localPermissionClient.deletePermission("perm-id-123") }
        verify(exactly = 1) { localPermissionClient.createPermission(any()) }
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
    fun `replicaPermissionRequest - FederationReplicaContext should be cleared after execution`() {
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "PROJECT",
            projectId = "p",
            permName = "n"
        )
        every { localPermissionClient.listPermission(any(), any(), any()) } returns ok(emptyList())
        every { localPermissionClient.createPermission(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        assertFalse(FederationReplicaContext.isFederationWrite())
    }

    @Test
    fun `replicaPermissionRequest UPSERT - permission with null id should skip delete`() {
        val existing = buildPermission("no-id-perm", "proj-1", "REPO", id = null)
        val request = PermissionReplicaRequest(
            action = ReplicaAction.UPSERT,
            resourceType = "REPO",
            projectId = "proj-1",
            permName = "no-id-perm"
        )
        every { localPermissionClient.listPermission("proj-1", null, "REPO") } returns ok(listOf(existing))
        every { localPermissionClient.createPermission(any()) } returns ok(true)

        controller.replicaPermissionRequest(request)

        verify(exactly = 0) { localPermissionClient.deletePermission(any()) }
        verify(exactly = 1) { localPermissionClient.createPermission(any()) }
    }

    // ==================== helper builders ====================

    private fun <T> ok(data: T? = null) = Response(CommonMessageCode.SUCCESS.getCode(), data = data)

    private fun buildUserInfo(userId: String, name: String, admin: Boolean): UserInfo {
        return UserInfo(
            userId = userId,
            name = name,
            email = null,
            phone = null,
            createdDate = LocalDateTime.now(),
            locked = false,
            admin = admin,
            group = false,
            asstUsers = emptyList(),
            tenantId = null
        )
    }

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
