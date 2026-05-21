/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.service.bkdevops

import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.model.TPersonalPath
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.cleanup.CleanMemberResult
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * [StaleMemberCleanServiceImpl] 核心场景单元测试。
 *
 * 覆盖：
 *  - 名单聚合：4 类残留点 → 候选集 → bk-ci 三态过滤 → 命中数填充
 *  - bk-ci 异常用户：计入 errorCount，不入选名单
 *  - 空候选短路：直接返回空列表 + 全 0 stats
 *  - 清理流程：二次确认 IS_MEMBER / UNKNOWN 拒绝
 *  - 唯一管理员守门
 *  - 自我清理拦截
 *  - 部分步骤失败仍继续执行其它步骤
 *  - 重复触发 30s 拦截
 *  - 配置缺失抛 AUTH_DEVOPS_CONFIG_MISSING
 */
class StaleMemberCleanServiceImplTest {

    private val ciAuthService: CIAuthService = mockk(relaxed = true)
    private val devopsAuthConfig: DevopsAuthConfig = mockk(relaxed = true)
    private val roleRepository: RoleRepository = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val permissionDao: PermissionDao = mockk(relaxed = true)
    private val personalPathDao: PersonalPathDao = mockk(relaxed = true)

    private lateinit var service: StaleMemberCleanServiceImpl

    private val projectId = "ut-project"
    private val operator = "ut-operator"

    @BeforeEach
    fun setUp() {
        service = StaleMemberCleanServiceImpl(
            ciAuthService,
            devopsAuthConfig,
            roleRepository,
            userDao,
            permissionDao,
            personalPathDao,
        )
        // 默认配置完整
        every { devopsAuthConfig.getBkciAuthServer() } returns "http://bkci-test.local"
        every { devopsAuthConfig.getBkciAuthToken() } returns "test-token"
    }

    // -------------------- listStaleMembers --------------------

    @Test
    @DisplayName("空候选 → 立即返回空列表与全 0 stats，不调用 bk-ci")
    fun emptyCandidateShortCircuit() {
        every { roleRepository.findByProjectIdAndTypeIn(projectId, any()) } returns emptyList()
        every { permissionDao.listByProjectId(projectId) } returns emptyList()
        every { personalPathDao.listByProject(projectId) } returns emptyList()

        val resp = service.listStaleMembers(projectId)

        assertEquals(0, resp.stats.candidateCount)
        assertEquals(0, resp.stats.confirmedStaleCount)
        assertEquals(0, resp.stats.confirmedMemberCount)
        assertEquals(0, resp.stats.errorCount)
        assertTrue(resp.members.isEmpty())
        verify(exactly = 0) { ciAuthService.probeProjectMember(any(), any()) }
    }

    @Test
    @DisplayName("名单聚合：bk-ci 异常计入 errorCount 并跳过；命中数正确填充")
    fun aggregateAndFilterWithErrorCount() {
        // 角色：1 个 PROJECT 角色 + 1 个 REPO 角色
        val projectRole = roleOf(id = "rid-pa", roleId = PROJECT_MANAGE_ID, type = RoleType.PROJECT, admin = true)
        val repoRole = roleOf(id = "rid-ra", roleId = "repo_manage", type = RoleType.REPO, admin = true)
        every {
            roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        } returns listOf(projectRole, repoRole)

        // userA 持有 PROJECT 角色（候选用户 1）
        val userA = userOf("userA", roles = listOf("rid-pa"))
        // userB 持有 REPO 角色（候选用户 2）
        val userB = userOf("userB", roles = listOf("rid-ra"))
        // userC 仅出现在 permission（候选用户 3）
        // userD 仅出现在 personal_path（候选用户 4）

        every { userDao.findAllByRolesIn(listOf("rid-pa")) } returns listOf(userA)
        every { userDao.findAllByRolesIn(listOf("rid-ra")) } returns listOf(userB)
        every { permissionDao.listByProjectId(projectId) } returns listOf(
            permissionOf(users = listOf("userA", "userC")),
            permissionOf(users = listOf("userC")),
        )
        every { personalPathDao.listByProject(projectId) } returns listOf(
            personalPathOf("userD"),
            personalPathOf("userD"),
        )
        every { userDao.findFirstByUserId(any()) } answers {
            val uid = firstArg<String>()
            userOf(uid)
        }

        // bk-ci：A=NOT_MEMBER、B=IS_MEMBER、C=UNKNOWN、D=NOT_MEMBER
        every { ciAuthService.probeProjectMember("userA", projectId) } returns MembershipProbeResult.NOT_MEMBER
        every { ciAuthService.probeProjectMember("userB", projectId) } returns MembershipProbeResult.IS_MEMBER
        every { ciAuthService.probeProjectMember("userC", projectId) } returns MembershipProbeResult.UNKNOWN
        every { ciAuthService.probeProjectMember("userD", projectId) } returns MembershipProbeResult.NOT_MEMBER

        val resp = service.listStaleMembers(projectId)

        assertEquals(4, resp.stats.candidateCount)
        assertEquals(2, resp.stats.confirmedStaleCount)
        assertEquals(1, resp.stats.confirmedMemberCount)
        assertEquals(1, resp.stats.errorCount)

        val ids = resp.members.map { it.userId }.toSet()
        // userA / userD 入选；userB / userC 不入选
        assertTrue("userA" in ids)
        assertTrue("userD" in ids)
        assertFalse("userB" in ids)
        assertFalse("userC" in ids)

        // 命中数：userA 拥有 1 个 PROJECT 角色 + 1 条 permission 命中
        val a = resp.members.first { it.userId == "userA" }
        assertEquals(1, a.projectRoleCount)
        assertEquals(1, a.permissionCount)
        assertEquals(0, a.repoRoleCount)
        assertEquals(0, a.personalPathCount)

        // userD 仅 personal_path 命中 2 次
        val d = resp.members.first { it.userId == "userD" }
        assertEquals(0, d.projectRoleCount)
        assertEquals(0, d.permissionCount)
        assertEquals(0, d.repoRoleCount)
        assertEquals(2, d.personalPathCount)
    }

    @Test
    @DisplayName("配置缺失 → 抛 AUTH_DEVOPS_CONFIG_MISSING")
    fun configMissingThrows() {
        every { devopsAuthConfig.getBkciAuthServer() } returns "http://"
        every { devopsAuthConfig.getBkciAuthToken() } returns ""
        assertThrows(ErrorCodeException::class.java) { service.listStaleMembers(projectId) }
    }

    // -------------------- cleanMember --------------------

    @Test
    @DisplayName("自我清理 → 拒绝（accepted=false）")
    fun selfCleanRejected() {
        val r = service.cleanMember(projectId, operator, operator)
        assertFalse(r.accepted)
        assertNotNull(r.reason)
        // 不进入二次确认
        verify(exactly = 0) { ciAuthService.probeProjectMember(any(), any()) }
    }

    @Test
    @DisplayName("二次确认 IS_MEMBER → 拒绝清理")
    fun stillMemberRejected() {
        every { ciAuthService.probeProjectMember("target", projectId) } returns MembershipProbeResult.IS_MEMBER
        val r = service.cleanMember(projectId, "target", operator)
        assertFalse(r.accepted)
        assertNotNull(r.reason)
        verify(exactly = 0) { permissionDao.pullUserFromAllInProject(any(), any()) }
    }

    @Test
    @DisplayName("二次确认 UNKNOWN → 拒绝且不占用 30s 锁（允许稍后重试）")
    fun probeUnknownRejectedAndUnlocked() {
        every { ciAuthService.probeProjectMember("target", projectId) } returns MembershipProbeResult.UNKNOWN
        val r1 = service.cleanMember(projectId, "target", operator)
        assertFalse(r1.accepted)

        // 立即再试，应该仍能进入二次确认（不会被"重复触发"拦截在外）
        every { ciAuthService.probeProjectMember("target", projectId) } returns MembershipProbeResult.UNKNOWN
        val r2 = service.cleanMember(projectId, "target", operator)
        assertFalse(r2.accepted)
        // 两次都进入了 probe，说明 UNKNOWN 时锁被撤销
        verify(atLeast = 2) { ciAuthService.probeProjectMember("target", projectId) }
    }

    @Test
    @DisplayName("唯一管理员守门 → 拒绝")
    fun lastAdminRejected() {
        every { ciAuthService.probeProjectMember("target", projectId) } returns MembershipProbeResult.NOT_MEMBER
        val adminRole = roleOf(id = "rid-admin", roleId = PROJECT_MANAGE_ID, type = RoleType.PROJECT, admin = true)
        every {
            roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        } returns listOf(adminRole)
        // 仅 target 一个人持有该 admin 角色
        every { userDao.findAllByRolesIn(listOf("rid-admin")) } returns listOf(
            userOf("target", roles = listOf("rid-admin"))
        )

        val r = service.cleanMember(projectId, "target", operator)

        assertFalse(r.accepted)
        assertTrue(r.reason!!.contains("only local project admin"))
        verify(exactly = 0) { permissionDao.pullUserFromAllInProject(any(), any()) }
    }

    @Test
    @DisplayName("正常清理：4 步全部执行，影响行数累计正确")
    fun fullCleanHappyPath() {
        every { ciAuthService.probeProjectMember("target", projectId) } returns MembershipProbeResult.NOT_MEMBER
        // 角色：管理员角色目标用户不持有，避免唯一管理员拦截
        val adminRole = roleOf(id = "rid-admin", roleId = PROJECT_MANAGE_ID, type = RoleType.PROJECT, admin = true)
        val repoRole = roleOf(id = "rid-repo", roleId = "repo_manage", type = RoleType.REPO, admin = true)
        every {
            roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        } returns listOf(adminRole, repoRole)
        every { userDao.findAllByRolesIn(listOf("rid-admin")) } returns listOf(
            userOf("someoneElse", roles = listOf("rid-admin"))
        )
        every { permissionDao.pullUserFromAllInProject(projectId, "target") } returns 3L
        every { userDao.pullRolesFromUser("target", listOf("rid-admin")) } returns 1L
        every { userDao.pullRolesFromUser("target", listOf("rid-repo")) } returns 1L
        every { personalPathDao.deleteByProjectAndUser(projectId, "target") } returns 5L

        val r = service.cleanMember(projectId, "target", operator)

        assertTrue(r.accepted)
        assertEquals(4, r.steps.size)
        val byStep = r.steps.associateBy { it.step }
        assertEquals(3L, byStep[CleanMemberResult.STEP_PERMISSION]!!.affected)
        assertEquals(1L, byStep[CleanMemberResult.STEP_PROJECT_ROLE]!!.affected)
        assertEquals(1L, byStep[CleanMemberResult.STEP_REPO_ROLE]!!.affected)
        assertEquals(5L, byStep[CleanMemberResult.STEP_PERSONAL_PATH]!!.affected)
        assertTrue(r.steps.all { it.success })
    }

    @Test
    @DisplayName("部分步骤失败：仅该步 success=false，其余步骤照常执行")
    fun partialFailureContinue() {
        every { ciAuthService.probeProjectMember("target", projectId) } returns MembershipProbeResult.NOT_MEMBER
        every {
            roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        } returns emptyList()
        every { permissionDao.pullUserFromAllInProject(projectId, "target") } throws RuntimeException("mongo down")
        every { userDao.pullRolesFromUser("target", emptyList()) } returns 0L
        every { personalPathDao.deleteByProjectAndUser(projectId, "target") } returns 2L

        val r = service.cleanMember(projectId, "target", operator)

        assertTrue(r.accepted)
        val byStep = r.steps.associateBy { it.step }
        assertFalse(byStep[CleanMemberResult.STEP_PERMISSION]!!.success)
        assertEquals("mongo down", byStep[CleanMemberResult.STEP_PERMISSION]!!.reason)
        // 其余步骤照常执行
        assertTrue(byStep[CleanMemberResult.STEP_PROJECT_ROLE]!!.success)
        assertTrue(byStep[CleanMemberResult.STEP_REPO_ROLE]!!.success)
        assertTrue(byStep[CleanMemberResult.STEP_PERSONAL_PATH]!!.success)
        assertEquals(2L, byStep[CleanMemberResult.STEP_PERSONAL_PATH]!!.affected)
    }

    @Test
    @DisplayName("30s 内重复触发 → 第二次直接拒绝")
    fun duplicateWithin30sRejected() {
        every { ciAuthService.probeProjectMember("target", projectId) } returns MembershipProbeResult.NOT_MEMBER
        every {
            roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        } returns emptyList()
        every { permissionDao.pullUserFromAllInProject(projectId, "target") } returns 0L
        every { userDao.pullRolesFromUser("target", emptyList()) } returns 0L
        every { personalPathDao.deleteByProjectAndUser(projectId, "target") } returns 0L

        val r1 = service.cleanMember(projectId, "target", operator)
        assertTrue(r1.accepted)

        val r2 = service.cleanMember(projectId, "target", operator)
        assertFalse(r2.accepted)
        assertNotNull(r2.reason)
        assertTrue(r2.reason!!.contains("duplicate"))
    }

    // -------------------- helpers --------------------

    private fun roleOf(
        id: String,
        roleId: String,
        type: RoleType,
        admin: Boolean = false,
    ): TRole = TRole(
        id = id,
        roleId = roleId,
        type = type,
        name = roleId,
        projectId = projectId,
        admin = admin,
    )

    private fun userOf(userId: String, roles: List<String> = emptyList()): TUser =
        TUser(userId = userId, name = userId, pwd = "x", roles = roles)

    private fun permissionOf(users: List<String>): TPermission = TPermission(
        id = "p-${users.joinToString("-")}",
        resourceType = ResourceType.REPO.name,
        projectId = projectId,
        permName = "ut",
        users = users,
        createBy = "ut",
        createAt = LocalDateTime.now(),
        updatedBy = "ut",
        updateAt = LocalDateTime.now(),
    )

    private fun personalPathOf(userId: String): TPersonalPath = TPersonalPath(
        projectId = projectId,
        repoName = "ut-repo",
        fullPath = "/foo",
        userId = userId,
    )
}
