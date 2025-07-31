package com.tencent.bkrepo.repository.service.schedule.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.exception.PermissionException
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
import org.springframework.util.StringUtils
import java.time.LocalDateTime
import java.util.regex.Pattern

@Service
class ScheduledDownloadRuleServiceImpl(
    private val scheduledDownloadRuleDao: ScheduledDownloadRuleDao,
    private val permissionManager: PermissionManager,
) : ScheduledDownloadRuleService {
    override fun create(request: UserScheduledDownloadRuleCreateRequest): ScheduledDownloadRule {
        with(request) {
            checkParams(cron, fullPathRegex, downloadDir)
            requireNotNull(operator)
            val now = LocalDateTime.now()
            val ruleUserIds = if (scope == ScheduledDownloadRuleScope.PROJECT) {
                userIds
            } else {
                setOf(operator!!)
            }
            checkUpdatePermission(projectId, operator!!, scope, ruleUserIds)
            // 创建规则
            val rule = scheduledDownloadRuleDao.insert(
                TScheduledDownloadRule(
                    createdBy = operator!!,
                    createdDate = now,
                    lastModifiedBy = operator!!,
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
                    scope = scope
                )
            )

            logger.info("user[$operator] create scheduled rule[${rule.id}] of project[$projectId] success ")
            return convert(rule, ruleUserIds)
        }
    }

    override fun remove(id: String, operator: String) {
        val rule = getRule(id)
        checkUpdatePermission(rule.projectId, operator, rule.scope, rule.userIds)
        scheduledDownloadRuleDao.removeById(id)
        logger.info("[$operator] remove scheduled rule[$id] of project[${rule.projectId}] success ")
    }

    override fun update(request: UserScheduledDownloadRuleUpdateRequest): ScheduledDownloadRule {
        with(request) {
            checkParams(cron, fullPathRegex, downloadDir)
            requireNotNull(operator)
            val oldRule = getRule(id)
            val ruleUserIds = if (oldRule.scope == ScheduledDownloadRuleScope.PROJECT) {
                userIds
            } else {
                // USER范围的预约规则用户列表不支持修改
                oldRule.userIds
            }

            // 校验权限
            checkUpdatePermission(oldRule.projectId, operator!!, oldRule.scope, ruleUserIds)

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

            logger.info("[$operator] update scheduled rule[${oldRule.id}] of project[${oldRule.projectId}] success ")
            return convert(scheduledDownloadRuleDao.save(oldRule), ruleUserIds)
        }
    }

    override fun projectRules(request: UserScheduledDownloadRuleQueryRequest): Page<ScheduledDownloadRule> {
        with(request) {
            requireNotNull(operator)

            // 仅允许查询单项目的项目级规则
            if (projectIds.size != 1) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, projectIds)
            }

            // 权限校验
            permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectIds.first(), operator!!)

            // 构造查询条件
            val criteria = buildQueryCriteria(request)
                .and(TScheduledDownloadRule::scope.name).isEqualTo(ScheduledDownloadRuleScope.PROJECT)
            if (!userIds.isNullOrEmpty()) {
                criteria.and(TScheduledDownloadRule::userIds.name).inValues(userIds!!)
            }

            // 执行查询
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = Query(criteria)
            val count = scheduledDownloadRuleDao.count(query)
            val rules = scheduledDownloadRuleDao.find(query.with(pageRequest)).map { convert(it, it.userIds) }
            return Pages.ofResponse(pageRequest, count, rules)
        }
    }

    override fun rules(request: UserScheduledDownloadRuleQueryRequest): Page<ScheduledDownloadRule> {
        with(request) {
            requireNotNull(operator)
            // 权限校验
            projectIds.forEach { permissionManager.checkProjectPermission(PermissionAction.DOWNLOAD, it, operator!!) }

            // 构造查询条件
            val criteria = buildQueryCriteria(request).orOperator(
                TScheduledDownloadRule::userIds.size(0),
                TScheduledDownloadRule::userIds.isEqualTo(null),
                TScheduledDownloadRule::userIds.inValues(operator!!),
            )

            // 执行查询
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = Query(criteria)
            val count = scheduledDownloadRuleDao.count(query)
            val ruleUserIds = setOf(operator!!)
            val rules = scheduledDownloadRuleDao.find(query.with(pageRequest)).map { convert(it, ruleUserIds) }
            return Pages.ofResponse(pageRequest, count, rules)
        }
    }

    override fun get(id: String, operator: String): ScheduledDownloadRule {
        val rule = getRule(id)
        if (rule.scope == ScheduledDownloadRuleScope.PROJECT && isProjectManager(operator, rule.projectId)) {
            return convert(rule, rule.userIds)
        }

        // 有项目下载权限且用户在userId列表里表示有权限，userId列表为空时表示规则对所有用户生效
        permissionManager.checkProjectPermission(PermissionAction.DOWNLOAD, rule.projectId, operator)
        if (!rule.userIds.isNullOrEmpty() && operator !in rule.userIds!!) {
            throw PermissionException()
        }

        return convert(rule, setOf(operator))
    }

    private fun checkParams(
        cron: String?,
        fullPathRegex: String?,
        downloadDir: String?,
    ) {
        cron?.let {
            val cronSplits = StringUtils.tokenizeToStringArray(it, " ");
            var valid = false
            if (cronSplits.size == 6) {
                // 0 0 2 1 * ?
                valid = CronExpression.isValidExpression(cron)
            } else if (cronSplits.size == 7) {
                // 0 15 10 ? * 6L 2025
                valid = CronExpression.isValidExpression(cron.substringBeforeLast(" "))
                try {
                    cronSplits[6].toInt()
                } catch (e: NumberFormatException) {
                    valid = false
                }
            }

            if (!valid) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, cron)
            }
        }

        try {
            fullPathRegex?.let { Pattern.compile(it) }
        } catch (e: Exception) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, fullPathRegex ?: "fullPathRegex")
        }

        if ((downloadDir?.length ?: 0) > MAX_DOWNLOAD_DIR_LENGTH) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, downloadDir ?: "downloadDir")
        }
    }

    private fun buildQueryCriteria(request: UserScheduledDownloadRuleQueryRequest): Criteria {
        with(request) {
            val criteria = Criteria()
                .and(TScheduledDownloadRule::projectId.name).inValues(projectIds)

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
            return criteria
        }
    }

    private fun getRule(id: String): TScheduledDownloadRule {
        return scheduledDownloadRuleDao.findById(id)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
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
            permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId, operator)
        } else {
            // 需要拥有项目下载权限并且拥有该预约规则才有权限
            permissionManager.checkProjectPermission(PermissionAction.DOWNLOAD, projectId, operator)
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
        private const val MAX_DOWNLOAD_DIR_LENGTH = 4096
    }
}
