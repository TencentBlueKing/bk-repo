package com.tencent.bkrepo.repository.service.schedule.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.dao.ScheduledDownloadRuleDao
import com.tencent.bkrepo.repository.model.TScheduledDownloadRule
import com.tencent.bkrepo.repository.pojo.schedule.MetadataRule
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadRule
import com.tencent.bkrepo.repository.pojo.schedule.ScheduledDownloadRuleScope
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.UserScheduledDownloadRuleUpdateRequest
import com.tencent.bkrepo.repository.service.schedule.ScheduledDownloadRuleService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.size
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScheduledDownloadRuleServiceImpl(
    private val scheduledDownloadRuleDao: ScheduledDownloadRuleDao,
    private val permissionManager: PermissionManager,
) : ScheduledDownloadRuleService {
    override fun create(request: UserScheduledDownloadRuleCreateRequest): ScheduledDownloadRule {
        with(request) {
            val now = LocalDateTime.now()
            val operator = SecurityUtils.getUserId()
            checkParams(cron)
            val ruleUserIds = if (scope == ScheduledDownloadRuleScope.PROJECT) {
                userIds
            } else {
                setOf(operator)
            }
            checkUpdatePermission(projectId, operator, scope, ruleUserIds)
            // 创建规则
            val rule = scheduledDownloadRuleDao.insert(
                TScheduledDownloadRule(
                    createdBy = operator,
                    createdDate = now,
                    lastModifiedBy = operator,
                    lastModifiedDate = now,
                    userIds = ruleUserIds,
                    projectId = projectId,
                    repoNames = repoNames,
                    fullPathRegex = fullPathRegex,
                    metadataRules = metadataRules,
                    cron = cron,
                    downloadDir = downloadDir,
                    conflictStrategy = conflictStrategy,
                    enabled = enabled,
                    platform = platform,
                )
            )

            logger.info("user[$operator] create scheduled rule[${rule.id}] of project[$projectId] success ")
            return convert(rule, userIds)
        }
    }

    override fun remove(id: String) {
        val rule = getRule(id)
        checkUpdatePermission(rule.projectId, SecurityUtils.getUserId(), rule.scope, rule.userIds)
        scheduledDownloadRuleDao.removeById(id)
    }

    override fun update(request: UserScheduledDownloadRuleUpdateRequest): ScheduledDownloadRule {
        with(request) {
            checkParams(cron)
            val oldRule = getRule(id)
            val operator = SecurityUtils.getUserId()
            val ruleUserIds = if (oldRule.scope == ScheduledDownloadRuleScope.PROJECT) {
                userIds
            } else {
                // USER范围的预约规则用户列表不支持修改
                oldRule.userIds
            }

            // 校验权限
            checkUpdatePermission(oldRule.projectId, operator, oldRule.scope, ruleUserIds)

            // 更新规则
            ruleUserIds?.let { oldRule.userIds = it }
            repoNames?.let { oldRule.repoNames = it }
            fullPathRegex?.let { oldRule.fullPathRegex = it }
            metadataRules?.let { oldRule.metadataRules = it }
            cron?.let { oldRule.cron = it }
            downloadDir?.let { oldRule.downloadDir = it }
            conflictStrategy?.let { oldRule.conflictStrategy = it }
            enabled?.let { oldRule.enabled = it }
            platform?.let { oldRule.platform = it }

            return convert(scheduledDownloadRuleDao.save(oldRule), ruleUserIds)
        }
    }

    override fun page(request: UserScheduledDownloadRuleQueryRequest): Page<ScheduledDownloadRule> {
        with(request) {
            val operator = SecurityUtils.getUserId()
            // 权限校验
            val hasManagerPermission = isProjectManager(operator, projectId)
            if (!hasManagerPermission) {
                permissionManager.checkProjectPermission(PermissionAction.DOWNLOAD, projectId, operator)
            }

            // 构造查询条件
            val criteria = Criteria().and(TScheduledDownloadRule::projectId.name).isEqualTo(projectId)
            if (hasManagerPermission && !userIds.isNullOrEmpty()) {
                // 查询包含指定用户的规则，表示只查询项目级别的规则
                criteria.and(TScheduledDownloadRule::userIds.name).inValues(userIds!!)
                criteria.and(TScheduledDownloadRule::scope.name).isEqualTo(ScheduledDownloadRuleScope.PROJECT)
            } else {
                // 仅查询包含的当前用户的规则
                criteria.orOperator(
                    TScheduledDownloadRule::userIds.size(0),
                    TScheduledDownloadRule::userIds.isEqualTo(null),
                    TScheduledDownloadRule::userIds.inValues(operator),
                )
                scope?.let { criteria.and(TScheduledDownloadRule::scope.name).isEqualTo(it) }
            }
            if (!repoNames.isNullOrEmpty()) {
                criteria.and(TScheduledDownloadRule::repoNames.name).inValues(repoNames!!)
            }
            val metadataCriteria = metadataRules?.map { metadataRule ->
                val elemCriteria = Criteria().andOperator(
                    MetadataRule::key.isEqualTo(metadataRule.key),
                    MetadataRule::value.isEqualTo(metadataRule.value)
                )
                Criteria.where(TScheduledDownloadRule::metadataRules.name).elemMatch(elemCriteria)
            }
            if (!metadataCriteria.isNullOrEmpty()) {
                criteria.andOperator(metadataCriteria)
            }
            enabled?.let { criteria.and(TScheduledDownloadRule::enabled.name).isEqualTo(it) }
            platform?.let { criteria.and(TScheduledDownloadRule::platform.name).isEqualTo(it) }

            // 执行查询
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = Query(criteria)
            val count = scheduledDownloadRuleDao.count(query)
            val rules = scheduledDownloadRuleDao.find(query.with(pageRequest)).map {
                val ruleUserIds = if (hasManagerPermission) {
                    it.userIds
                } else {
                    setOf(operator)
                }
                convert(it, ruleUserIds)
            }
            return Pages.ofResponse(pageRequest, count, rules)
        }
    }

    override fun get(id: String): ScheduledDownloadRule {
        val rule = getRule(id)
        val operator = SecurityUtils.getUserId()
        if (isProjectManager(operator, rule.projectId)) {
            return convert(rule, rule.userIds)
        }

        // 有项目下载权限且用户在userId列表里表示有权限，userId列表为空时表示规则对所有用户生效
        permissionManager.checkProjectPermission(PermissionAction.DOWNLOAD, rule.projectId, operator)
        if (!rule.userIds.isNullOrEmpty() && operator !in rule.userIds!!) {
            throw PermissionException()
        }

        return convert(rule, setOf(operator))
    }

    private fun getRule(id: String): TScheduledDownloadRule {
        return scheduledDownloadRuleDao.findById(id)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
    }

    private fun checkParams(cron: String?) {
        if (cron != null && CronExpression.isValidExpression(cron)) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, cron)
        }
    }

    private fun isProjectManager(userId: String, projectId: String): Boolean {
        try {
            permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId, userId)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun checkUpdatePermission(
        projectId: String,
        operator: String,
        scope: ScheduledDownloadRuleScope,
        userIds: Set<String>?,
    ) {
        if (scope == ScheduledDownloadRuleScope.PROJECT) {
            permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId)
        } else {
            // 需要拥有项目下载权限并且拥有该预约规则才有权限
            permissionManager.checkProjectPermission(PermissionAction.DOWNLOAD, projectId)
            if (operator !in userIds!!) {
                throw PermissionException()
            }
        }
    }

    private fun convert(rule: TScheduledDownloadRule, userIds: Set<String>?) = with(rule) {
        ScheduledDownloadRule(
            id = id,
            userIds = userIds,
            projectId = projectId,
            repoNames = repoNames,
            fullPathRegex = fullPathRegex,
            metadataRules = metadataRules,
            cron = cron,
            downloadDir = downloadDir,
            conflictStrategy = conflictStrategy,
            enabled = enabled,
            platform = platform,
            scope = scope,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduledDownloadRuleServiceImpl::class.java)
    }
}
