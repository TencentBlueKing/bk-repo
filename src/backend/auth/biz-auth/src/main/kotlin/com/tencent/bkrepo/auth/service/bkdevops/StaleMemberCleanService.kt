/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.service.bkdevops

import com.tencent.bkrepo.auth.pojo.cleanup.CleanMemberResult
import com.tencent.bkrepo.auth.pojo.cleanup.StaleMemberListResponse

/**
 * DevOps 模式下"项目维度残留成员"清理服务。
 *
 * 设计原则：
 * 1. 失效判定的唯一权威依据是 [CIAuthService.isProjectMember] —— 不依赖 TUser.locked / source；
 * 2. bk-ci 接口异常时，对应用户视为"未确认"——既不出现在名单中、也不允许清理，避免误删；
 * 3. 仅清理项目维度残留，不删除 user 表中的用户本身（用户可能仍属于其它项目）；
 * 4. Bean 注册受 [com.tencent.bkrepo.auth.condition.DevopsAuthCondition] 控制，仅 DevOps 模式生效。
 */
interface StaleMemberCleanService {

    /**
     * 列出某个项目下"非 DevOps 项目成员"的残留用户清单（只读，不做任何修改）。
     *
     * 候选集合来自 4 类本地数据：permission.users、user.roles 反查（PROJECT/REPO 类型角色）、personal_path。
     *
     * @throws com.tencent.bkrepo.common.api.exception.ErrorCodeException
     *         当 bk-ci 必要配置缺失（ciAuthServer / ciAuthToken）时抛出
     */
    fun listStaleMembers(projectId: String): StaleMemberListResponse

    /**
     * 清理单个用户在某个项目下的全部本地权限残留。
     *
     * 流程：
     * 1. 二次确认 [CIAuthService.isProjectMember] 必须返回明确的"非成员"——返回 true 或异常都拒绝；
     * 2. 校验是否为该项目"本地项目管理员角色（PROJECT_MANAGE_ID）"中唯一剩余的成员，是则拒绝；
     * 3. 按 4 步独立 try/catch 清理：permission.users → PROJECT 角色 → REPO 角色 → personal_path；
     * 4. 任一步失败记录告警日志但继续后续步骤，最终聚合每步状态返回。
     *
     * @param operator 操作人（已通过项目管理员鉴权），用于审计与"自我清理"拦截
     */
    fun cleanMember(projectId: String, userId: String, operator: String): CleanMemberResult
}
