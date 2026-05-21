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
 * 项目维度"已不在 bk-ci 项目成员中、但在 bk-repo 本地仍有权限痕迹"的用户条目。
 *
 * 各 *Count 字段反映该用户在四类残留点的命中数量，用于辅助管理员判断"清理影响面"。
 */
@Schema(title = "项目维度残留成员条目")
data class StaleMemberInfo(
    @get:Schema(title = "用户 ID")
    val userId: String,
    @get:Schema(title = "用户姓名")
    val name: String,
    @get:Schema(title = "命中的 permission 文档数")
    val permissionCount: Int = 0,
    @get:Schema(title = "命中的 PROJECT 类型角色数")
    val projectRoleCount: Int = 0,
    @get:Schema(title = "命中的 REPO 类型角色数")
    val repoRoleCount: Int = 0,
    @get:Schema(title = "命中的 personal_path 记录数")
    val personalPathCount: Int = 0,
)
