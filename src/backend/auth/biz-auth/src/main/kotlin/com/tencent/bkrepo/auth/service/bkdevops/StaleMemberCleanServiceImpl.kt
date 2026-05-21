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
        fun touch(userId: String): HitCounters = map.getOrPut(userId) { HitCounters() }

        // 1) 项目下所有 PROJECT / REPO 类型角色 → 反查持有这些角色的 user
        val projectRoles = roleRepository.findByProjectIdAndTypeIn(projectId, listOf(RoleType.PROJECT, RoleType.REPO))
        if (projectRoles.isNotEmpty()) {
            val projectRoleIds = projectRoles.filter { it.type == RoleType.PROJECT }.mapNotNull { it.id }
            val repoRoleIds = projectRoles.filter { it.type == RoleType.REPO }.mapNotNull { it.id }
            if (projectRoleIds.isNotEmpty()) {
                userDao.findAllByRolesIn(projectRoleIds).forEach { user ->
                    val hitInProject = user.roles.count { it in projectRoleIds }
                    if (hitInProject > 0) touch(user.userId).projectRoleCount += hitInProject
                }
            }
            if (repoRoleIds.isNotEmpty()) {
                userDao.findAllByRolesIn(repoRoleIds).forEach { user ->
                    val hitInRepo = user.roles.count { it in repoRoleIds }
                    if (hitInRepo > 0) touch(user.userId).repoRoleCount += hitInRepo
                }
            }
        }

        // 2) permission 表中 projectId=该项目 的所有 permission 文档 → users 字段展开
        permissionDao.listByProjectId(projectId).forEach { permission ->
            permission.users.forEach { uid ->
                if (uid.isNotBlank()) touch(uid).permissionCount += 1
            }
        }

        // 3) personal_path 表中 projectId=该项目 的所有记录 → userId 展开
        personalPathDao.listByProject(projectId).forEach { path ->
            if (path.userId.isNotBlank()) touch(path.userId).personalPathCount += 1
        }

        return CandidateAggregation(userIdToHits = map)
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
