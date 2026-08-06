/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.helper

import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionContext
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 单元测试：[PermissionHelper.checkRepoActionInPermission]
 *
 * 覆盖需求验收标准：
 * - 1.1：仓库级授权命中放行
 * - 1.2：未授权拒绝
 * - 2.1：MANAGE 动作覆盖任意 action
 * - 2.2：具体 action 命中放行
 * - 2.3：action 不匹配且不含 MANAGE 不放行
 * - 7.1 / 7.2 / 7.4：常规放行/拒绝/MANAGE 全权放行
 */
class PermissionHelperRepoLevelGrantTest {

    private val userDao: UserDao = mockk(relaxed = true)
    private val roleRepository: RoleRepository = mockk(relaxed = true)
    private val permissionDao: PermissionDao = mockk(relaxed = true)
    private val personalPathDao: PersonalPathDao = mockk(relaxed = true)

    private val helper = PermissionHelper(userDao, roleRepository, permissionDao, personalPathDao)

    private val projectId = "ut-project"
    private val repoName = "ut-repo"
    private val userId = "ut-user"
    private val roles = listOf("role-1")

    private fun ctx(action: PermissionAction): CheckPermissionContext {
        return CheckPermissionContext(
            userId = userId,
            roles = roles,
            resourceType = ResourceType.REPO.name,
            action = action.name,
            projectId = projectId,
            repoName = repoName,
        )
    }

    private fun perm(actions: List<PermissionAction>): TPermission {
        return TPermission(
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
    }

    @Test
    @DisplayName("需求 1.1 / 7.1：仓库级 READ 授权命中放行")
    fun grantReadHit() {
        every {
            permissionDao.listPermissionInRepo(projectId, repoName, userId, roles)
        } returns listOf(perm(listOf(PermissionAction.READ)))
        assertTrue(helper.checkRepoActionInPermission(ctx(PermissionAction.READ)))
    }

    @Test
    @DisplayName("需求 1.2 / 7.2：无任何授权时拒绝")
    fun grantMiss() {
        every {
            permissionDao.listPermissionInRepo(projectId, repoName, userId, roles)
        } returns emptyList()
        assertFalse(helper.checkRepoActionInPermission(ctx(PermissionAction.READ)))
    }

    @Test
    @DisplayName("需求 2.1 / 7.4：MANAGE 授权可放行所有 action")
    fun manageAllowAll() {
        every {
            permissionDao.listPermissionInRepo(projectId, repoName, userId, roles)
        } returns listOf(perm(listOf(PermissionAction.MANAGE)))

        listOf(
            PermissionAction.READ,
            PermissionAction.WRITE,
            PermissionAction.DOWNLOAD,
            PermissionAction.UPDATE,
            PermissionAction.DELETE,
        ).forEach { action ->
            assertTrue(
                helper.checkRepoActionInPermission(ctx(action)),
                "MANAGE should allow $action",
            )
        }
    }

    @Test
    @DisplayName("需求 2.2：具体 action 命中放行")
    fun specificActionHit() {
        every {
            permissionDao.listPermissionInRepo(projectId, repoName, userId, roles)
        } returns listOf(perm(listOf(PermissionAction.WRITE, PermissionAction.DOWNLOAD)))
        assertTrue(helper.checkRepoActionInPermission(ctx(PermissionAction.WRITE)))
        assertTrue(helper.checkRepoActionInPermission(ctx(PermissionAction.DOWNLOAD)))
    }

    @Test
    @DisplayName("需求 2.3：action 不匹配且无 MANAGE 时不放行")
    fun specificActionMiss() {
        every {
            permissionDao.listPermissionInRepo(projectId, repoName, userId, roles)
        } returns listOf(perm(listOf(PermissionAction.READ)))
        assertFalse(helper.checkRepoActionInPermission(ctx(PermissionAction.WRITE)))
        assertFalse(helper.checkRepoActionInPermission(ctx(PermissionAction.DELETE)))
    }

    @Test
    @DisplayName("repoName 为空时返回 false")
    fun blankRepoName() {
        val context = CheckPermissionContext(
            userId = userId,
            roles = roles,
            resourceType = ResourceType.REPO.name,
            action = PermissionAction.READ.name,
            projectId = projectId,
            repoName = null,
        )
        assertFalse(helper.checkRepoActionInPermission(context))
    }
}
