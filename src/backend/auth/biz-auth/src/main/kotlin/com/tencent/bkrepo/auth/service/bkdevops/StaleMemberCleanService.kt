/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.service.bkdevops

import com.tencent.bkrepo.auth.pojo.cleanup.BatchCleanMembersResult
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

    /**
     * 批量清理：内部按顺序复用 [cleanMember] 处理每个用户。
     *
     * 设计要点：
     * 1. [userIds] 必填且非空，单批最多 [BATCH_CLEAN_MAX_SIZE] 个，超过抛 [com.tencent.bkrepo.common.api.exception.ErrorCodeException]；
     * 2. 自动去重并保留首次出现顺序；
     * 3. 串行执行 —— 充分利用 [CIAuthService] 的本地缓存、避免 burst 打到 bk-ci；
     * 4. **熔断**：连续出现 [BATCH_CLEAN_ABORT_THRESHOLD] 个 bk-ci 探测异常（UNKNOWN）即中止剩余清理，
     *    避免 bk-ci 故障期间的级联误判；
     * 5. **dryRun**：仅做"二次确认 + 唯一管理员"等只读校验，不写库；用于让管理员预览影响面。
     */
    fun cleanMembers(
        projectId: String,
        userIds: List<String>,
        operator: String,
        dryRun: Boolean = false,
    ): BatchCleanMembersResult

    /**
     * 一键清理项目下"全部已离职成员"。
     *
     * 流程：
     * 1. 复用 [listStaleMembers] 拿到全部 NOT_MEMBER 用户（UNKNOWN 已被排除）；
     * 2. 兜底校验名单规模 ≤ [maxCleanSize]，超过抛错并提示管理员改用 clean-batch 分批；
     * 3. 当名单大于 [BATCH_CLEAN_MAX_SIZE] 时按 100 一片自动分片，串行复用 [cleanMembers]；
     * 4. 任一片熔断（连续 N 次 bk-ci UNKNOWN）即整体中止剩余分片。
     *
     * 与 [cleanMembers] 的关系：本方法是它的"自动取数 + 自动分片"包装，**业务护栏完全一致**
     * （自我清理 / 二次确认 / 唯一管理员 / 30s 防抖 / 4 步独立 try-catch / UNKNOWN 熔断）。
     */
    fun cleanAllStaleMembers(
        projectId: String,
        operator: String,
        dryRun: Boolean = false,
        maxCleanSize: Int = DEFAULT_CLEAN_ALL_MAX_SIZE,
    ): BatchCleanMembersResult

    companion object {
        /** 批量清理单批最大允许数量。 */
        const val BATCH_CLEAN_MAX_SIZE = 100

        /** 连续 N 次 bk-ci UNKNOWN 即熔断。 */
        const val BATCH_CLEAN_ABORT_THRESHOLD = 5

        /** 一键清理默认上限（兜底闸，与 [com.tencent.bkrepo.auth.pojo.cleanup.CleanAllStaleMembersRequest.DEFAULT_MAX_CLEAN_ALL] 对齐）。 */
        const val DEFAULT_CLEAN_ALL_MAX_SIZE = 200
    }
}
