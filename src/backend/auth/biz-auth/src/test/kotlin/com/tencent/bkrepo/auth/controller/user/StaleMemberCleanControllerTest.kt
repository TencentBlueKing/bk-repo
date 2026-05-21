/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.controller.user

import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.cleanup.CleanMemberResult
import com.tencent.bkrepo.auth.pojo.cleanup.StaleMemberListResponse
import com.tencent.bkrepo.auth.pojo.cleanup.StaleMemberScanStats
import com.tencent.bkrepo.auth.service.bkdevops.DevopsProjectService
import com.tencent.bkrepo.auth.service.bkdevops.StaleMemberCleanService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [StaleMemberCleanController] 鉴权与窗口期校验测试。
 *
 * 关键点：
 * - 平台级 admin / 系统管理员**不享有越权调用权限**——必须通过 [DevopsProjectService.isProjectManager]
 * - 清理接口在 service 接受请求后还会再次校验"调用方仍是项目管理员"，覆盖窗口期问题
 */
class StaleMemberCleanControllerTest {

    private val service: StaleMemberCleanService = mockk(relaxed = true)
    private val devopsProjectService: DevopsProjectService = mockk(relaxed = true)

    private lateinit var controller: StaleMemberCleanController

    private val projectId = "ut-project"
    private val operator = "ut-operator"
    private val target = "ut-target"

    @BeforeEach
    fun setUp() {
        controller = StaleMemberCleanController(service, devopsProjectService)
        mockkObject(SecurityUtils)
        every { SecurityUtils.getUserId() } returns operator
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurityUtils)
    }

    @Test
    @DisplayName("非项目管理员调用名单接口 → 403")
    fun listRejectedWhenNotProjectManager() {
        every { devopsProjectService.isProjectManager(operator, projectId) } returns false
        val ex = assertThrows(ErrorCodeException::class.java) {
            controller.listStaleMembers(projectId)
        }
        assertEquals(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM, ex.messageCode)
        verify(exactly = 0) { service.listStaleMembers(any()) }
    }

    @Test
    @DisplayName("项目管理员调用名单接口 → 透传 service 结果")
    fun listSuccessWhenProjectManager() {
        every { devopsProjectService.isProjectManager(operator, projectId) } returns true
        val resp = StaleMemberListResponse(
            projectId = projectId,
            members = emptyList(),
            stats = StaleMemberScanStats(0, 0, 0, 0),
        )
        every { service.listStaleMembers(projectId) } returns resp

        val out = controller.listStaleMembers(projectId)

        assertEquals(0, out.code)
        assertEquals(resp, out.data)
    }

    @Test
    @DisplayName("非项目管理员调用清理接口 → 403")
    fun cleanRejectedWhenNotProjectManager() {
        every { devopsProjectService.isProjectManager(operator, projectId) } returns false
        val ex = assertThrows(ErrorCodeException::class.java) {
            controller.cleanMember(projectId, target)
        }
        assertEquals(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM, ex.messageCode)
        verify(exactly = 0) { service.cleanMember(any(), any(), any()) }
    }

    @Test
    @DisplayName("窗口期：service 返回 accepted=true 但调用方在执行间隙失去管理员 → 403")
    fun cleanRejectedWhenLostManagerDuringWindow() {
        every { devopsProjectService.isProjectManager(operator, projectId) } returnsMany listOf(true, false)
        every { service.cleanMember(projectId, target, operator) } returns CleanMemberResult(
            projectId = projectId,
            userId = target,
            operator = operator,
            accepted = true,
        )
        val ex = assertThrows(ErrorCodeException::class.java) {
            controller.cleanMember(projectId, target)
        }
        assertEquals(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM, ex.messageCode)
    }

    @Test
    @DisplayName("项目管理员清理 → 透传结果")
    fun cleanSuccessWhenProjectManager() {
        every { devopsProjectService.isProjectManager(operator, projectId) } returns true
        val r = CleanMemberResult(projectId, target, operator, accepted = true)
        every { service.cleanMember(projectId, target, operator) } returns r

        val out = controller.cleanMember(projectId, target)
        assertTrue(out.data!!.accepted)
        assertEquals(target, out.data!!.userId)
    }
}
