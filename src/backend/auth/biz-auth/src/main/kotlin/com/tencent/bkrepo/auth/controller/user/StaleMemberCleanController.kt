/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.controller.user

import com.tencent.bkrepo.auth.condition.DevopsAuthCondition
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.cleanup.CleanMemberResult
import com.tencent.bkrepo.auth.pojo.cleanup.StaleMemberListResponse
import com.tencent.bkrepo.auth.service.bkdevops.DevopsProjectService
import com.tencent.bkrepo.auth.service.bkdevops.StaleMemberCleanService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * DevOps 模式下"项目维度残留成员"治理入口。
 *
 * 设计要点：
 * - 仅在 DevOps 鉴权模式下注册（[DevopsAuthCondition]）
 * - 鉴权严格走 [DevopsProjectService.isProjectManager]（bk-ci 角色判定）；
 *   平台级 admin / 系统管理员**不享有越权调用权限**
 * - 清理接口在执行业务前再做一次"调用方仍是项目管理员"校验，覆盖窗口期问题
 */
@Tag(name = "项目残留成员治理")
@RestController
@RequestMapping("/api/devops/project/{projectId}/stale-members")
@Conditional(DevopsAuthCondition::class)
class StaleMemberCleanController(
    private val staleMemberCleanService: StaleMemberCleanService,
    private val devopsProjectService: DevopsProjectService,
) {

    @Operation(summary = "列出项目下'已不在 bk-ci 项目成员中、却仍在本地有权限痕迹'的用户名单")
    @GetMapping
    fun listStaleMembers(
        @PathVariable projectId: String,
    ): Response<StaleMemberListResponse> {
        val operator = currentUser()
        ensureProjectManager(operator, projectId)
        val response = staleMemberCleanService.listStaleMembers(projectId)
        logger.info(
            "stale-member list invoked: operator=[$operator] project=[$projectId] " +
                "candidate=[${response.stats.candidateCount}] stale=[${response.stats.confirmedStaleCount}] " +
                "member=[${response.stats.confirmedMemberCount}] error=[${response.stats.errorCount}]"
        )
        return ResponseBuilder.success(response)
    }

    @Operation(summary = "清理单个用户在该项目下的所有本地权限残留")
    @PostMapping("/{userId}/clean")
    fun cleanMember(
        @PathVariable projectId: String,
        @PathVariable userId: String,
    ): Response<CleanMemberResult> {
        val operator = currentUser()
        // 入口鉴权
        ensureProjectManager(operator, projectId)
        try {
            val result = staleMemberCleanService.cleanMember(projectId, userId, operator)
            // 业务执行前的"窗口期"再校验：如果调用方在执行间隙刚被移出管理员，则拒绝清理
            if (result.accepted && !devopsProjectService.isProjectManager(operator, projectId)) {
                logger.warn(
                    "operator [$operator] lost project-manager role during clean " +
                        "of project=[$projectId] target=[$userId]; reject post-hoc"
                )
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
            }
            auditClean(operator, projectId, userId, result)
            return ResponseBuilder.success(result)
        } catch (e: ErrorCodeException) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "clean stale member failed: operator=[$operator] project=[$projectId] target=[$userId]", e
            )
            throw e
        }
    }

    private fun currentUser(): String {
        val uid = SecurityUtils.getUserId()
        if (uid.isBlank()) throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        return uid
    }

    /**
     * 严格按 bk-ci 项目管理员判定：平台级 admin / 系统管理员不享有越权调用权限。
     */
    private fun ensureProjectManager(operator: String, projectId: String) {
        if (!devopsProjectService.isProjectManager(operator, projectId)) {
            logger.warn("user [$operator] is not project manager of [$projectId]; reject stale-member ops")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_FORAUTH_NOT_PERM)
        }
    }

    /**
     * 结构化审计日志：单独使用 [AUDIT_LOGGER]，便于运维侧落盘归档。
     *
     * 输出字段：操作人 / 时间戳由 logger 自带 / projectId / targetUserId / accepted /
     * 各步骤命中数与影响行数。
     */
    private fun auditClean(operator: String, projectId: String, userId: String, result: CleanMemberResult) {
        if (!result.accepted) {
            AUDIT_LOGGER.info(
                "STALE_MEMBER_CLEAN | operator=[{}] project=[{}] target=[{}] accepted=[false] reason=[{}]",
                operator, projectId, userId, result.reason
            )
            return
        }
        val stepDigest = result.steps.joinToString(separator = ";") {
            "${it.step}=${if (it.success) "ok" else "fail"}/${it.affected}"
        }
        AUDIT_LOGGER.info(
            "STALE_MEMBER_CLEAN | operator=[{}] project=[{}] target=[{}] accepted=[true] steps=[{}]",
            operator, projectId, userId, stepDigest
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StaleMemberCleanController::class.java)
        /** 独立的审计 logger，便于运维侧通过 logback 配置单独打到一份归档文件。 */
        private val AUDIT_LOGGER = LoggerFactory.getLogger("STALE_MEMBER_AUDIT")
    }
}
