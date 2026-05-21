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
 * 单步清理结果。
 *
 * - success：true 表示该步执行成功（即便 affected = 0，也算成功，仅代表无残留）；
 *            false 表示该步抛出异常被吞下，需要人工跟进
 * - affected：受影响的文档数（permission 文档数 / role 数 / personal_path 行数）
 * - reason：失败/拒绝时的简要说明，成功时为 null
 */
@Schema(title = "单步清理结果")
data class CleanStepResult(
    @get:Schema(title = "步骤名")
    val step: String,
    @get:Schema(title = "是否成功")
    val success: Boolean,
    @get:Schema(title = "受影响数量")
    val affected: Long,
    @get:Schema(title = "失败/拒绝原因")
    val reason: String? = null,
)

/**
 * 清理接口的整体响应。
 *
 * - accepted=false 表示请求未进入实际清理流程（如二次确认未通过、唯一管理员、自我清理、重复触发等），
 *   此时 [steps] 为空，[reason] 提供拒绝原因；
 * - accepted=true 表示进入清理流程，[steps] 给出每一步的成败 + 影响行数（即便整体有部分失败也会落库）。
 */
@Schema(title = "清理结果")
data class CleanMemberResult(
    @get:Schema(title = "项目 ID")
    val projectId: String,
    @get:Schema(title = "目标用户 ID")
    val userId: String,
    @get:Schema(title = "操作人")
    val operator: String,
    @get:Schema(title = "是否进入清理流程")
    val accepted: Boolean,
    @get:Schema(title = "未进入流程时的拒绝原因")
    val reason: String? = null,
    @get:Schema(title = "各步骤结果")
    val steps: List<CleanStepResult> = emptyList(),
) {
    companion object {
        const val STEP_PERMISSION = "permission.users"
        const val STEP_PROJECT_ROLE = "user.roles[PROJECT]"
        const val STEP_REPO_ROLE = "user.roles[REPO]"
        const val STEP_PERSONAL_PATH = "personal_path"
    }
}
