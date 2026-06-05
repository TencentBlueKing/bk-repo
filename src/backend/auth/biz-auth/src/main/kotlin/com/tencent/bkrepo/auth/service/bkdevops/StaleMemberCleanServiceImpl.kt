/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.auth.service.bkdevops

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.auth.condition.DevopsAuthCondition
import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.cleanup.BatchCleanMembersResult
import com.tencent.bkrepo.auth.pojo.cleanup.CleanMemberResult
import com.tencent.bkrepo.auth.pojo.cleanup.CleanStepResult
import com.tencent.bkrepo.auth.pojo.cleanup.StaleMemberInfo
import com.tencent.bkrepo.auth.pojo.cleanup.StaleMemberListResponse
import com.tencent.bkrepo.auth.pojo.cleanup.StaleMemberScanStats
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * 见 [StaleMemberCleanService] 顶层注释。
 *
 * 实现要点：
 * 1. 候选集聚合 → 三态确认 → 名单/清理；
 * 2. bk-ci 调用通过 [CIAuthService.probeProjectMember] 区分 IS_MEMBER / NOT_MEMBER / UNKNOWN；
 * 3. "重复触发"通过本地 Caffeine/Guava Cache（30s TTL）拦截，防止管理员误连点；
 * 4. 清理步骤独立 try/catch，出现部分失败不会影响其它步骤。
 */
@Service
@Conditional(DevopsAuthCondition::class)
class StaleMemberCleanServiceImpl(
    private val ciAuthService: CIAuthService,
    private val devopsAuthConfig: DevopsAuthConfig,
    private val roleRepository: RoleRepository,
    private val userDao: UserDao,
    private val permissionDao: PermissionDao,
    private val personalPathDao: PersonalPathDao,
) : StaleMemberCleanService {

    /** 30s 内同一 (projectId, userId) 不允许重复触发清理。仅缓存 key 即可，存 Long 时间戳便于排错。 */
    private val recentCleanCache = CacheBuilder.newBuilder()
        .maximumSize(RECENT_CLEAN_CACHE_SIZE)
        .expireAfterWrite(RECENT_CLEAN_TTL_SECONDS, TimeUnit.SECONDS)
        .build<String, Long>()

    override fun listStaleMembers(projectId: String): StaleMemberListResponse {
        ensureDevopsConfigured()
        val candidates = collectCandidates(projectId)
        if (candidates.userIdToHits.isEmpty()) {
            return StaleMemberListResponse(
                projectId = projectId,
                members = emptyList(),
                stats = StaleMemberScanStats(0, 0, 0, 0),
            )
        }

        val sortedUserIds = candidates.userIdToHits.keys.toList()
        var confirmedStale = 0
        var confirmedMember = 0
        var errorCount = 0

        // 分批处理，避免一次性发起过多对外请求；CIAuthService 自身有 60s 缓存，
        // 短时间内重复扫描同一项目候选用户时不会重复打到 bk-ci。
        val staleEntries = mutableListOf<StaleMemberInfo>()
        sortedUserIds.chunked(PROBE_BATCH_SIZE).forEach { batch ->
            batch.forEach { userId ->
                when (ciAuthService.probeProjectMember(userId, projectId)) {
                    MembershipProbeResult.NOT_MEMBER -> {
                        confirmedStale++
                        staleEntries += buildStaleMemberInfo(userId, candidates)
                    }
                    MembershipProbeResult.IS_MEMBER -> confirmedMember++
                    MembershipProbeResult.UNKNOWN -> errorCount++
                }
            }
        }

        return StaleMemberListResponse(
            projectId = projectId,
            members = staleEntries.sortedBy { it.userId },
            stats = StaleMemberScanStats(
                candidateCount = candidates.userIdToHits.size,
                confirmedStaleCount = confirmedStale,
                confirmedMemberCount = confirmedMember,
                errorCount = errorCount,
            ),
        )
    }

    override fun cleanMember(projectId: String, userId: String, operator: String): CleanMemberResult {
        return doCleanMember(projectId, userId, operator, dryRun = false)
    }

    override fun cleanAllStaleMembers(
        projectId: String,
        operator: String,
        dryRun: Boolean,
        maxCleanSize: Int,
    ): BatchCleanMembersResult {
        ensureDevopsConfigured()

        // ① 复用 listStaleMembers 取数（已经做了 bk-ci 三态确认，UNKNOWN 不会出现在名单里）
        val stale = listStaleMembers(projectId).members.map { it.userId }
        if (stale.isEmpty()) {
            logger.info("clean-all noop: project=[$projectId] operator=[$operator] no stale member found")
            return BatchCleanMembersResult(
                projectId = projectId,
                operator = operator,
                dryRun = dryRun,
                total = 0,
                accepted = 0,
                rejected = 0,
                aborted = false,
                abortReason = null,
                results = emptyList(),
            )
        }

        // ② 兜底闸：名单异常膨胀时拒绝整体清理，提示管理员降低 maxCleanSize 或缩小项目范围
        if (stale.size > maxCleanSize) {
            throw ErrorCodeException(
                AuthMessageCode.AUTH_DEVOPS_CLEAN_ALL_TOO_MANY,
                stale.size.toString(),
                maxCleanSize.toString(),
            )
        }

        // ③ 串行执行 + UNKNOWN 熔断：每个用户独立走 doCleanMember 的全部护栏
        val results = ArrayList<CleanMemberResult>(stale.size)
        var consecutiveUnknown = 0
        var aborted = false
        var abortReason: String? = null
        for (uid in stale) {
            val r = doCleanMember(projectId, uid, operator, dryRun)
            results += r
            // 仅当原因是 "bk-ci probe failed" 时计入连续异常计数；其它拒绝原因不触发熔断
            if (!r.accepted && r.reason?.startsWith("bk-ci probe failed") == true) {
                consecutiveUnknown += 1
                if (consecutiveUnknown >= StaleMemberCleanService.CLEAN_ALL_ABORT_THRESHOLD) {
                    aborted = true
                    abortReason = "aborted after $consecutiveUnknown consecutive bk-ci probe failures"
                    logger.warn(
                        "clean-all aborted: project=[$projectId] operator=[$operator] " +
                            "processed=[${results.size}/${stale.size}] reason=[$abortReason]"
                    )
                    break
                }
            } else {
                consecutiveUnknown = 0
            }
        }

        val acceptedCount = results.count { it.accepted }
        return BatchCleanMembersResult(
            projectId = projectId,
            operator = operator,
            dryRun = dryRun,
            total = stale.size,
            accepted = acceptedCount,
            rejected = results.size - acceptedCount,
            aborted = aborted,
            abortReason = abortReason,
            results = results,
        )
    }

    /**
     * 实际清理逻辑：覆盖单用户与批量两条入口。
     *
     * @param dryRun true 时仅完成"前置校验 + 二次确认"，不写库；返回的 [CleanMemberResult.steps] 为空。
     *   注意：dryRun 也会写"30s 防抖"缓存，避免与真实清理交错。
     */
    private fun doCleanMember(
        projectId: String,
        userId: String,
        operator: String,
        dryRun: Boolean,
    ): CleanMemberResult {
        ensureDevopsConfigured()

        // 自我清理拦截
        if (operator == userId) {
            return CleanMemberResult(
                projectId = projectId,
                userId = userId,
                operator = operator,
                accepted = false,
                reason = "operator cannot clean himself",
            )
        }

        // 30s 防重复触发拦截
        val recentKey = "$projectId::$userId"
        if (recentCleanCache.getIfPresent(recentKey) != null) {
            return CleanMemberResult(
                projectId = projectId,
                userId = userId,
                operator = operator,
                accepted = false,
                reason = "duplicate clean within ${RECENT_CLEAN_TTL_SECONDS}s",
            )
        }
        recentCleanCache.put(recentKey, System.currentTimeMillis())

        // 二次确认：明确"非成员"才放行；返回 true（仍是成员）或 UNKNOWN（接口异常）都拒绝
        when (ciAuthService.probeProjectMember(userId, projectId)) {
            MembershipProbeResult.IS_MEMBER -> return CleanMemberResult(
                projectId = projectId,
                userId = userId,
                operator = operator,
                accepted = false,
                reason = "user is still a project member in bk-ci",
            )
            MembershipProbeResult.UNKNOWN -> {
                // 不要因为接口失败就阻塞 30s——把 key 撤掉，让管理员可以稍后重试
                recentCleanCache.invalidate(recentKey)
                return CleanMemberResult(
                    projectId = projectId,
                    userId = userId,
                    operator = operator,
                    accepted = false,
                    reason = "bk-ci probe failed; cleanup rejected to avoid mistaken deletion",
                )
            }
            MembershipProbeResult.NOT_MEMBER -> Unit
        }

        // 收集该项目下角色，并区分 PROJECT / REPO 两类
        val projectRoles = roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        val projectTypeRoleIds = projectRoles.filter { it.type == RoleType.PROJECT }.mapNotNull { it.id }
        val repoTypeRoleIds = projectRoles.filter { it.type == RoleType.REPO }.mapNotNull { it.id }

        // 唯一管理员守门
        val lastAdminCheck = checkLastProjectAdmin(projectRoles, userId)
        if (lastAdminCheck != null) {
            return CleanMemberResult(
                projectId = projectId,
                userId = userId,
                operator = operator,
                accepted = false,
                reason = lastAdminCheck,
            )
        }

        // dryRun 模式：所有前置校验已通过，但不实际写库。
        if (dryRun) {
            return CleanMemberResult(
                projectId = projectId,
                userId = userId,
                operator = operator,
                accepted = true,
                reason = "dry-run: would clean (no writes)",
                steps = emptyList(),
            )
        }

        // 进入清理流程：每步独立 try/catch，最大化清理
        val steps = listOf(
            runStep(CleanMemberResult.STEP_PERMISSION) {
                permissionDao.pullUserFromAllInProject(projectId, userId)
            },
            runStep(CleanMemberResult.STEP_PROJECT_ROLE) {
                userDao.pullRolesFromUser(userId, projectTypeRoleIds)
            },
            runStep(CleanMemberResult.STEP_REPO_ROLE) {
                userDao.pullRolesFromUser(userId, repoTypeRoleIds)
            },
            runStep(CleanMemberResult.STEP_PERSONAL_PATH) {
                personalPathDao.deleteByProjectAndUser(projectId, userId)
            },
        )

        return CleanMemberResult(
            projectId = projectId,
            userId = userId,
            operator = operator,
            accepted = true,
            steps = steps,
        )
    }

    private fun ensureDevopsConfigured() {
        if (devopsAuthConfig.getBkciAuthToken().isBlank() ||
            devopsAuthConfig.getBkciAuthServer().removePrefix("http://").removePrefix("https://").isBlank()
        ) {
            throw ErrorCodeException(AuthMessageCode.AUTH_DEVOPS_CONFIG_MISSING)
        }
    }

    /** 候选集合：userId → 各类残留点命中次数。 */
    private data class CandidateAggregation(
        val userIdToHits: Map<String, HitCounters>,
    )

    private data class HitCounters(
        var permissionCount: Int = 0,
        var projectRoleCount: Int = 0,
        var repoRoleCount: Int = 0,
        var personalPathCount: Int = 0,
    )

    /**
     * 聚合 4 类本地数据中"项目维度"的 userId → 命中次数。
     *
     * 注意：
     * - role 反查使用 user.roles 字段（mongo `_id` 列表），通过 [UserDao.findAllByRolesIn] 拿到对应用户；
     * - permission.users 是用户 ID 列表，直接展开；
     * - personal_path 直接按 projectId 拉取，userId 字段展开计数。
     */
    private fun collectCandidates(projectId: String): CandidateAggregation {
        val map = HashMap<String, HitCounters>()

        // 1) 项目下所有 PROJECT / REPO 类型角色 → 反查持有这些角色的 user
        aggregateRoleHits(projectId, map)

        // 2) permission 表中 projectId=该项目 的所有 permission 文档 → users 字段展开
        aggregatePermissionHits(projectId, map)

        // 3) personal_path 表中 projectId=该项目 的所有记录 → userId 展开
        aggregatePersonalPathHits(projectId, map)

        return CandidateAggregation(userIdToHits = map)
    }

    /** 角色维度聚合：分别处理 PROJECT 与 REPO 类型角色的反查与计数。 */
    private fun aggregateRoleHits(projectId: String, map: HashMap<String, HitCounters>) {
        val projectRoles = roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        if (projectRoles.isEmpty()) return
        val projectRoleIds = projectRoles.filter { it.type == RoleType.PROJECT }.mapNotNull { it.id }
        val repoRoleIds = projectRoles.filter { it.type == RoleType.REPO }.mapNotNull { it.id }
        countRoleHits(projectRoleIds, map) { counters, hits -> counters.projectRoleCount += hits }
        countRoleHits(repoRoleIds, map) { counters, hits -> counters.repoRoleCount += hits }
    }

    /**
     * 通用的"角色 ID 集合 → 反查持有用户 → 累加命中次数"小工具。
     *
     * 抽出此方法既能让 [collectCandidates] 嵌套层级达标，也复用了 PROJECT / REPO 两类角色的相同流程。
     */
    private inline fun countRoleHits(
        roleIds: List<String>,
        map: HashMap<String, HitCounters>,
        crossinline accumulate: (HitCounters, Int) -> Unit,
    ) {
        if (roleIds.isEmpty()) return
        userDao.findAllByRolesIn(roleIds).forEach { user ->
            val hits = user.roles.count { it in roleIds }
            if (hits > 0) accumulate(map.getOrPut(user.userId) { HitCounters() }, hits)
        }
    }

    /** permission 维度聚合：展开 permission.users 列表逐个累加。 */
    private fun aggregatePermissionHits(projectId: String, map: HashMap<String, HitCounters>) {
        permissionDao.listByProjectId(projectId).forEach { permission ->
            permission.users.forEach { uid ->
                if (uid.isNotBlank()) map.getOrPut(uid) { HitCounters() }.permissionCount += 1
            }
        }
    }

    /** personal_path 维度聚合：按 projectId 拉取后展开 userId 计数。 */
    private fun aggregatePersonalPathHits(projectId: String, map: HashMap<String, HitCounters>) {
        personalPathDao.listByProject(projectId).forEach { path ->
            if (path.userId.isNotBlank()) map.getOrPut(path.userId) { HitCounters() }.personalPathCount += 1
        }
    }

    private fun buildStaleMemberInfo(userId: String, candidates: CandidateAggregation): StaleMemberInfo {
        val hits = candidates.userIdToHits[userId] ?: HitCounters()
        // 用户 name 优先取 user 表，找不到时退化为 userId 自身
        val name = userDao.findFirstByUserId(userId)?.name ?: userId
        return StaleMemberInfo(
            userId = userId,
            name = name,
            permissionCount = hits.permissionCount,
            projectRoleCount = hits.projectRoleCount,
            repoRoleCount = hits.repoRoleCount,
            personalPathCount = hits.personalPathCount,
        )
    }

    /**
     * 校验"是否为项目唯一本地管理员"。
     *
     * 本地"项目管理员角色"以 [PROJECT_MANAGE_ID] 为 roleId 标记。如果目标用户是该角色当前
     * 唯一持有者，必须拒绝清理，避免项目陷入"无人接管"状态。
     *
     * @return 拒绝原因（人类可读），null 代表通过校验
     */
    private fun checkLastProjectAdmin(projectRoles: List<TRole>, userId: String): String? {
        val adminRole = projectRoles.firstOrNull {
            it.type == RoleType.PROJECT && it.roleId == PROJECT_MANAGE_ID && it.admin
        } ?: return null
        val adminRoleId = adminRole.id ?: return null
        val admins = userDao.findAllByRolesIn(listOf(adminRoleId))
            .filter { adminRoleId in it.roles }
        return when {
            admins.none { it.userId == userId } -> null // 目标用户根本不是管理员，无需守门
            admins.size <= 1 -> "user is the only local project admin; refuse to clean"
            else -> null
        }
    }

    private fun runStep(name: String, action: () -> Long): CleanStepResult {
        return try {
            val affected = action()
            CleanStepResult(step = name, success = true, affected = affected)
        } catch (e: Exception) {
            logger.warn("clean step [$name] failed: ${e.message}", e)
            CleanStepResult(step = name, success = false, affected = 0L, reason = e.message)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StaleMemberCleanServiceImpl::class.java)

        /** 每批探测多少个 userId（控制 bk-ci 调用 burst）。 */
        private const val PROBE_BATCH_SIZE = 50

        /** 30 秒内同一 (projectId, userId) 不允许重复触发清理。 */
        private const val RECENT_CLEAN_TTL_SECONDS = 30L
        private const val RECENT_CLEAN_CACHE_SIZE = 5_000L
    }
}
