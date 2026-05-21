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
 * 名单接口的扫描统计：辅助管理员判断"扫描完整性"。
 *
 * - candidateCount：本地聚合得到的候选 userId 总数（去重后）
 * - confirmedStaleCount：经 bk-ci 确认"已不在项目"的用户数（即最终入选名单的人数）
 * - confirmedMemberCount：经 bk-ci 确认"仍在项目"的用户数（已被剔除）
 * - errorCount：调用 bk-ci 接口异常导致"未确认"的用户数（既未入选名单也不允许清理）
 */
@Schema(title = "残留成员扫描统计")
data class StaleMemberScanStats(
    @get:Schema(title = "候选用户总数（去重后）")
    val candidateCount: Int,
    @get:Schema(title = "已确认非项目成员数（最终名单数）")
    val confirmedStaleCount: Int,
    @get:Schema(title = "已确认仍是项目成员数")
    val confirmedMemberCount: Int,
    @get:Schema(title = "bk-ci 接口异常数（未确认）")
    val errorCount: Int,
)

/**
 * 名单接口的整体响应：成员列表 + 扫描统计。
 */
@Schema(title = "项目残留成员名单响应")
data class StaleMemberListResponse(
    @get:Schema(title = "项目 ID")
    val projectId: String,
    @get:Schema(title = "残留成员列表")
    val members: List<StaleMemberInfo>,
    @get:Schema(title = "扫描统计")
    val stats: StaleMemberScanStats,
)
