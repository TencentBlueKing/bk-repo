/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.dao.AccountDao
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.RepoAuthConfigDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 单元测试：[PermissionServiceImpl.listPermissionRepo] 严格模式可见性过滤。
 *
 * 验证场景：
 * - 项目下无严格模式仓库 → 快速退出（零 grant 查询）
 * - 项目用户对部分严格仓库无授权 → 被过滤掉
 * - 项目用户对严格仓库有显式授权 → 保留
 * - 系统管理员 / 项目管理员 → 不参与过滤
 */
class PermissionServiceImplListRepoStrictFilterTest {

    private val roleRepository: RoleRepository = mockk(relaxed = true)
    private val accountDao: AccountDao = mockk(relaxed = true)
    private val permissionDao: PermissionDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val personalPathDao: PersonalPathDao = mockk(relaxed = true)
    private val repoAuthConfigDao: RepoAuthConfigDao = mockk(relaxed = true)
    private val repositoryService: RepositoryService = mockk(relaxed = true)
    private val projectService: ProjectService = mockk(relaxed = true)

    private lateinit var service: PermissionServiceImpl

    private val projectId = "ut-project"
    private val userId = "ut-user"
    private val repoA = "repo-a"
    private val repoB = "repo-b-strict"
    private val repoC = "repo-c-strict"

    @BeforeEach
    fun setUp() {
        service = PermissionServiceImpl(
            roleRepository,
            accountDao,
            permissionDao,
            userDao,
            personalPathDao,
            repoAuthConfigDao,
            repositoryService,
            projectService,
        )
        // 默认仓库列表：repoA(普通) + repoB(严格) + repoC(严格)
        every { repositoryService.listRepo(projectId) } returns listOf(
            mockRepoInfo(repoA),
            mockRepoInfo(repoB),
            mockRepoInfo(repoC),
        )
        // mockk(relaxed = true) 对 nullable 返回类型默认会构造一个 mock 对象（非 null），
        // 这里显式 stub 为 null，避免 isUserLocalProjectAdmin / isGlobalPreviewUser
        // 等基于 ?: return false 的判定被误命中。
        every { userDao.findFirstByUserIdAndRolesIn(any(), any()) } returns null
        every { roleRepository.findFirstByTypeAndRoleId(any(), any()) } returns null
    }

    private fun mockRepoInfo(name: String): RepositoryInfo {
        val info: RepositoryInfo = mockk()
        every { info.name } returns name
        return info
    }

    private fun mockUser(admin: Boolean = false, roles: List<String> = emptyList()): TUser {
        val user = TUser(userId = userId, name = userId, pwd = "x", admin = admin, roles = roles)
        every { userDao.findFirstByUserId(userId) } returns user
        return user
    }

    /** 不让用户成为系统/项目管理员或项目用户。 */
    private fun mockNotProjectAdminOrUser(roles: List<String> = emptyList()) {
        // isUserLocalProjectAdmin -> false：项目下没有 admin 角色匹配
        every {
            roleRepository.findByTypeAndProjectIdAndAdmin(RoleType.PROJECT, projectId, true)
        } returns emptyList()
        // isUserLocalProjectUser -> false：用户角色查回来不含 PROJECT_VIEWER_ID
        every { roleRepository.findAllById(roles) } returns emptyList()
    }

    /** 让用户成为项目用户（走过滤分支）。 */
    private fun mockAsProjectUser(roles: List<String>) {
        // isUserLocalProjectAdmin -> false
        every {
            roleRepository.findByTypeAndProjectIdAndAdmin(RoleType.PROJECT, projectId, true)
        } returns emptyList()
        // isUserLocalProjectUser -> true：roles 中存在一个 (projectId, PROJECT_VIEWER_ID) 角色
        val viewerRole = TRole(
            id = roles.first(),
            roleId = com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID,
            type = RoleType.PROJECT,
            name = "viewer",
            projectId = projectId,
            admin = false,
        )
        every { roleRepository.findAllById(roles) } returns listOf(viewerRole)
    }

    private fun grantedPermission(repoName: String): TPermission {
        return TPermission(
            id = "p-$repoName",
            resourceType = ResourceType.REPO.name,
            projectId = projectId,
            permName = "ut",
            repos = listOf(repoName),
            users = listOf(userId),
            roles = emptyList(),
            actions = listOf(PermissionAction.READ.name),
            createBy = "ut",
            createAt = LocalDateTime.now(),
            updatedBy = "ut",
            updateAt = LocalDateTime.now(),
        )
    }

    @Test
    @DisplayName("admin 用户：不参与严格模式过滤，返回全量")
    fun adminBypassFilter() {
        mockUser(admin = true)
        val result = service.listPermissionRepo(projectId, userId, null)
        assertEquals(listOf(repoA, repoB, repoC), result)
        // admin 路径不应触发严格模式查询
        verify(exactly = 0) { repoAuthConfigDao.listRepoNamesByMode(any(), any()) }
    }

    @Test
    @DisplayName("项目用户 + 项目下无严格模式仓库 → 快速退出，不查 grant")
    fun projectUserFastReturnWhenNoStrict() {
        val roles = listOf("role-viewer")
        mockUser(roles = roles)
        mockAsProjectUser(roles)
        every {
            repoAuthConfigDao.listRepoNamesByMode(projectId, AccessControlMode.STRICT)
        } returns emptySet()

        val result = service.listPermissionRepo(projectId, userId, null)

        assertEquals(listOf(repoA, repoB, repoC), result)
        // 快速退出：不应查授权
        verify(exactly = 0) { permissionDao.listByProjectIdAndUsers(any(), any()) }
        verify(exactly = 0) { permissionDao.listByProjectAndRoles(any(), any()) }
    }

    @Test
    @DisplayName("项目用户 + 严格模式仓库未授权 → 被过滤")
    fun projectUserStrictWithoutGrantFilteredOut() {
        val roles = listOf("role-viewer")
        mockUser(roles = roles)
        mockAsProjectUser(roles)
        every {
            repoAuthConfigDao.listRepoNamesByMode(projectId, AccessControlMode.STRICT)
        } returns setOf(repoB, repoC)
        every { permissionDao.listByProjectIdAndUsers(projectId, userId) } returns emptyList()
        every { permissionDao.listByProjectAndRoles(projectId, roles) } returns emptyList()

        val result = service.listPermissionRepo(projectId, userId, null)

        // repoB / repoC 未授权 → 被剔除，仅保留普通仓库 repoA
        assertEquals(listOf(repoA), result)
    }

    @Test
    @DisplayName("项目用户 + 严格模式仓库已授权 → 保留")
    fun projectUserStrictWithGrantKept() {
        val roles = listOf("role-viewer")
        mockUser(roles = roles)
        mockAsProjectUser(roles)
        every {
            repoAuthConfigDao.listRepoNamesByMode(projectId, AccessControlMode.STRICT)
        } returns setOf(repoB, repoC)
        // 用户对 repoB 有授权，repoC 无授权
        every {
            permissionDao.listByProjectIdAndUsers(projectId, userId)
        } returns listOf(grantedPermission(repoB))
        every { permissionDao.listByProjectAndRoles(projectId, roles) } returns emptyList()

        val result = service.listPermissionRepo(projectId, userId, null)

        // 保留：repoA(普通) + repoB(严格已授权)；repoC(严格未授权) 被剔除
        assertTrue(result.containsAll(listOf(repoA, repoB)))
        assertFalse(result.contains(repoC))
        assertEquals(2, result.size)
    }

    @Test
    @DisplayName("非项目用户走聚合分支 → 不调用严格模式过滤")
    fun nonProjectUserUseAggregationPathNoFilter() {
        val roles = emptyList<String>()
        mockUser(roles = roles)
        mockNotProjectAdminOrUser(roles)
        // 聚合路径仅返回用户已授权仓库，无需过滤
        every {
            permissionDao.listByProjectIdAndUsers(projectId, userId)
        } returns listOf(grantedPermission(repoB))

        val result = service.listPermissionRepo(projectId, userId, null)

        assertEquals(listOf(repoB), result)
        // 严格模式过滤不应被触发
        verify(exactly = 0) { repoAuthConfigDao.listRepoNamesByMode(any(), any()) }
    }
}
