/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.pojo.cleanup

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 批量清理请求体。
 *
 * 设计要点：
 * - [userIds] 必填且非空：禁止"空列表 = 清理全部"这种危险语义，必须由前端先调用名单接口、
 *   人工二次确认勾选后再传入；
 * - [dryRun] 为 true 时，仅复用单用户清理流程中的"二次确认 + 唯一管理员守门"等校验，
 *   不会执行实际写库（受 service 实现保证）。
 */
@Schema(title = "批量清理请求")
data class BatchCleanMembersRequest(
    @get:Schema(title = "目标用户 ID 列表（必填且非空，单批最多 100 个）")
    val userIds: List<String>,
    @get:Schema(title = "演练模式：仅返回'将要清理'的预览，不实际写库")
    val dryRun: Boolean = false,
)

/**
 * 批量清理整体结果。
 *
 * - [aborted] 为 true 表示出现连续 N 个 UNKNOWN（bk-ci 探测异常）后熔断，剩余用户未尝试清理；
 * - [results] 顺序与请求中 [BatchCleanMembersRequest.userIds] 顺序一致；
 *   若 aborted=true，则后段未尝试的用户不会出现在 [results] 中。
 */
@Schema(title = "批量清理结果")
data class BatchCleanMembersResult(
    @get:Schema(title = "项目 ID")
    val projectId: String,
    @get:Schema(title = "操作人")
    val operator: String,
    @get:Schema(title = "演练模式")
    val dryRun: Boolean,
    @get:Schema(title = "请求总数")
    val total: Int,
    @get:Schema(title = "进入清理流程并完成的用户数（accepted=true 的数量）")
    val accepted: Int,
    @get:Schema(title = "被规则拒绝的用户数（accepted=false 的数量）")
    val rejected: Int,
    @get:Schema(title = "是否因 bk-ci 连续异常被熔断")
    val aborted: Boolean,
    @get:Schema(title = "熔断原因，仅在 aborted=true 时有值")
    val abortReason: String? = null,
    @get:Schema(title = "每个用户的明细结果（顺序与请求一致）")
    val results: List<CleanMemberResult>,
)

/**
 * 一键清理"项目下全部已离职成员"请求体。
 *
 * - 不需要传 userIds：服务端先调用 [com.tencent.bkrepo.auth.service.bkdevops.StaleMemberCleanService.listStaleMembers]
 *   拿到名单（已通过 bk-ci 三态确认，UNKNOWN 不会进入名单），再串行清理；
 * - [maxCleanSize] 是兜底闸：当 stale 名单异常膨胀时拒绝清理，提示管理员改用 clean-batch 分批；
 *   生产值默认见 [DEFAULT_MAX_CLEAN_ALL]。
 */
@Schema(title = "一键清理项目全部已离职成员请求")
data class CleanAllStaleMembersRequest(
    @get:Schema(title = "演练模式：仅返回'将要清理'的预览，不实际写库")
    val dryRun: Boolean = false,
    @get:Schema(
        title = "本次操作允许清理的最大人数上限（防止 stale 名单异常膨胀时的兜底保护）",
    )
    val maxCleanSize: Int = DEFAULT_MAX_CLEAN_ALL,
) {
    companion object {
        /** 单次 clean-all 默认允许清理的最大人数上限。 */
        const val DEFAULT_MAX_CLEAN_ALL = 200
    }
}
