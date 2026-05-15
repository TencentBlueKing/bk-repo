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
import com.tencent.bkrepo.auth.model.TRepoAuthConfig
import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionContext
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 单元测试：[PermissionServiceImpl.isGenericRepo] 与 [PermissionServiceImpl.checkStrictRepoLevelGrant]
 *
 * 覆盖需求验收标准：
 * - 1.1：严格模式 + 非 Generic 仓库 + 仓库级 READ → true
 * - 1.4 / 7.3：严格模式 + Generic 仓库 + 仅仓库级 READ → false（不引入新行为）
 * - 4.1：通过 RepositoryService.getRepoDetail 取 type
 * - 4.2 / 7.5：仓库详情查询失败/不存在 → 兜底拒绝放行
 */
class PermissionServiceImplStrictRepoGrantTest {

    private val roleRepository: RoleRepository = mockk(relaxed = true)
    private val accountDao: AccountDao = mockk(relaxed = true)
    private val permissionDao: PermissionDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val personalPathDao: PersonalPathDao = mockk(relaxed = true)
    private val repoAuthConfigDao: RepoAuthConfigDao = mockk(relaxed = true)
    private val repositoryService: RepositoryService = mockk(relaxed = true)
    private val projectService: ProjectService = mockk(relaxed = true)

    private lateinit var service: TestablePermissionServiceImpl

    private val projectId = "ut-project"
    private val repoName = "ut-repo"
    private val userId = "ut-user"
    private val roles = listOf("role-1")

    /** 暴露 protected 方法用于测试。 */
    private class TestablePermissionServiceImpl(
        roleRepository: RoleRepository,
        accountDao: AccountDao,
        permissionDao: PermissionDao,
        userDao: UserDao,
        personalPathDao: PersonalPathDao,
        repoAuthConfigDao: RepoAuthConfigDao,
        repositoryService: RepositoryService,
        projectService: ProjectService,
    ) : PermissionServiceImpl(
        roleRepository,
        accountDao,
        permissionDao,
        userDao,
        personalPathDao,
        repoAuthConfigDao,
        repositoryService,
        projectService,
    ) {
        fun callIsGenericRepo(projectId: String, repoName: String): Boolean? =
            isGenericRepo(projectId, repoName)

        fun callCheckStrictRepoLevelGrant(context: CheckPermissionContext): Boolean =
            checkStrictRepoLevelGrant(context)
    }

    @BeforeEach
    fun setUp() {
        service = TestablePermissionServiceImpl(
            roleRepository,
            accountDao,
            permissionDao,
            userDao,
            personalPathDao,
            repoAuthConfigDao,
            repositoryService,
            projectService,
        )
    }

    private fun ctx(action: PermissionAction = PermissionAction.READ): CheckPermissionContext {
        return CheckPermissionContext(
            userId = userId,
            roles = roles,
            resourceType = ResourceType.REPO.name,
            action = action.name,
            projectId = projectId,
            repoName = repoName,
        )
    }

    private fun mockStrictMode(strict: Boolean) {
        val cfg = TRepoAuthConfig(
            id = "cfg",
            projectId = projectId,
            repoName = repoName,
            accessControl = true,
            accessControlMode = if (strict) AccessControlMode.STRICT else AccessControlMode.DEFAULT,
            officeDenyGroupSet = null,
            lastModifiedBy = "ut",
            lastModifiedDate = LocalDateTime.now(),
            bkiamv3Check = null,
        )
        every { repoAuthConfigDao.findOneByProjectRepo(projectId, repoName) } returns cfg
    }

    private fun mockRepoType(type: RepositoryType?) {
        if (type == null) {
            every { repositoryService.getRepoDetail(projectId, repoName) } returns null
        } else {
            val detail: RepositoryDetail = mockk()
            every { detail.type } returns type
            every { repositoryService.getRepoDetail(projectId, repoName) } returns detail
        }
    }

    private fun mockRepoLevelGrant(actions: List<PermissionAction>) {
        val perm = TPermission(
            id = "p1",
            resourceType = ResourceType.REPO.name,
            projectId = projectId,
            permName = "ut-perm",
            repos = listOf(repoName),
            users = listOf(userId),
            roles = roles,
            actions = actions.map { it.name },
            createBy = "ut",
            createAt = LocalDateTime.now(),
            updatedBy = "ut",
            updateAt = LocalDateTime.now(),
        )
        every {
            permissionDao.listPermissionInRepo(projectId, repoName, userId, roles)
        } returns listOf(perm)
    }

    @Test
    @DisplayName("需求 4.1：getRepoDetail 返回 GENERIC → isGenericRepo=true")
    fun isGenericRepoTrue() {
        mockRepoType(RepositoryType.GENERIC)
        assertTrue(service.callIsGenericRepo(projectId, repoName)!!)
    }

    @Test
    @DisplayName("需求 4.1：getRepoDetail 返回 DOCKER → isGenericRepo=false")
    fun isGenericRepoFalse() {
        mockRepoType(RepositoryType.DOCKER)
        assertFalse(service.callIsGenericRepo(projectId, repoName)!!)
    }

    @Test
    @DisplayName("需求 4.2：仓库详情为 null → isGenericRepo 返回 null（兜底）")
    fun isGenericRepoNullWhenDetailMissing() {
        mockRepoType(null)
        assertNull(service.callIsGenericRepo(projectId, repoName))
    }

    @Test
    @DisplayName("需求 4.2 / 7.5：getRepoDetail 抛异常 → isGenericRepo 返回 null（兜底）")
    fun isGenericRepoNullWhenException() {
        every { repositoryService.getRepoDetail(projectId, repoName) } throws RuntimeException("boom")
        assertNull(service.callIsGenericRepo(projectId, repoName))
    }

    @Test
    @DisplayName("需求 1.1：严格模式 + Docker 仓库 + 仓库级 READ 授权 → 放行")
    fun strictNonGenericGrantHit() {
        mockStrictMode(strict = true)
        mockRepoType(RepositoryType.DOCKER)
        mockRepoLevelGrant(listOf(PermissionAction.READ))
        assertTrue(service.callCheckStrictRepoLevelGrant(ctx(PermissionAction.READ)))
    }

    @Test
    @DisplayName("需求 1.4 / 7.3：严格模式 + Generic 仓库 + 仅仓库级 READ → 不放行")
    fun strictGenericKeepLegacyBehavior() {
        mockStrictMode(strict = true)
        mockRepoType(RepositoryType.GENERIC)
        mockRepoLevelGrant(listOf(PermissionAction.READ))
        assertFalse(service.callCheckStrictRepoLevelGrant(ctx(PermissionAction.READ)))
    }

    @Test
    @DisplayName("需求 1.5：非严格模式 → 不进入仓库级整仓授权分支")
    fun nonStrictModeNotEnter() {
        mockStrictMode(strict = false)
        mockRepoType(RepositoryType.DOCKER)
        mockRepoLevelGrant(listOf(PermissionAction.READ))
        assertFalse(service.callCheckStrictRepoLevelGrant(ctx(PermissionAction.READ)))
    }

    @Test
    @DisplayName("需求 4.2：仓库类型查询失败 → 不放行（兜底拒绝）")
    fun strictRepoTypeUnknownDeny() {
        mockStrictMode(strict = true)
        every { repositoryService.getRepoDetail(projectId, repoName) } throws RuntimeException("boom")
        mockRepoLevelGrant(listOf(PermissionAction.READ))
        assertFalse(service.callCheckStrictRepoLevelGrant(ctx(PermissionAction.READ)))
    }
}
