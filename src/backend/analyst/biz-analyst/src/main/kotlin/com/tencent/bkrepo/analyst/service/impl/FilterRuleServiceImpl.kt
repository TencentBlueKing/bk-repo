/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.dao.FilterRuleDao
import com.tencent.bkrepo.analyst.model.TFilterRule
import com.tencent.bkrepo.analyst.pojo.Constant.SYSTEM_PROJECT_ID
import com.tencent.bkrepo.analyst.pojo.request.filter.ListFilterRuleRequest
import com.tencent.bkrepo.analyst.pojo.request.filter.MatchFilterRuleRequest
import com.tencent.bkrepo.analyst.pojo.request.filter.UpdateFilterRuleRequest
import com.tencent.bkrepo.analyst.pojo.response.filter.FilterRule
import com.tencent.bkrepo.analyst.pojo.response.filter.MergedFilterRule
import com.tencent.bkrepo.analyst.service.FilterRuleService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FilterRuleServiceImpl(private val filterRuleDao: FilterRuleDao) : FilterRuleService {
    override fun create(request: UpdateFilterRuleRequest): FilterRule {
        with(request) {
            check()
            if (filterRuleDao.exists(projectId, name)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name)
            }
            logger.info("Create ignore rule[$request] success")
            return filterRuleDao.insert(toPO()).toDTO()
        }
    }

    override fun delete(ruleId: String): Boolean {
        return delete(SYSTEM_PROJECT_ID, ruleId)
    }

    override fun delete(projectId: String, ruleId: String): Boolean {
        if (filterRuleDao.remove(projectId, ruleId)) {
            return true
        } else {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, ruleId)
        }
    }

    override fun update(request: UpdateFilterRuleRequest): FilterRule {
        logger.info("Update ignore rule[$request]")
        request.check()
        return filterRuleDao.update(request)?.toDTO()
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, "ruleId[${request.id}]")
    }

    override fun get(ruleId: String): FilterRule {
        return filterRuleDao.findById(ruleId)?.toDTO()
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, ruleId)
    }

    override fun list(request: ListFilterRuleRequest): Page<FilterRule> {
        val rules = filterRuleDao.list(
            request.projectId,
            request.planId,
            Pages.ofRequest(request.pageNumber, request.pageSize)
        )
        return Pages.buildPage(rules.records.map { it.toDTO() }, request.pageNumber, request.pageSize)
    }

    override fun match(request: MatchFilterRuleRequest): MergedFilterRule {
        with(request) {
            val rules = filterRuleDao.match(request)

            val matchedRules = ArrayList<FilterRule>(rules.size)
            for (rule in rules) {
                var matched = true
                if (rule.fullPath?.isNotEmpty() == true) {
                    matched = matched && Regex(rule.fullPath).matches(fullPath ?: "")
                }

                if (rule.packageKey?.isNotEmpty() == true) {
                    matched = matched && Regex(rule.packageKey).matches(packageKey ?: "")
                }

                if (rule.packageVersion?.isNotEmpty() == true) {
                    matched = matched && Regex(rule.packageVersion).matches(packageVersion ?: "")
                }

                if (matched) {
                    matchedRules.add(rule.toDTO())
                }
            }
            return merge(matchedRules)
        }
    }

    private fun merge(rules: List<FilterRule>): MergedFilterRule {
        val mergedFilterRule = MergedFilterRule()

        // 先添加系统规则，后添加项目定的规则，项目定的规则可以覆盖系统规则
        rules
            .filter { it.projectId == SYSTEM_PROJECT_ID }
            .forEach { mergedFilterRule.add(it) }

        rules
            .filter { it.projectId != SYSTEM_PROJECT_ID }
            .forEach { mergedFilterRule.add(it) }
        return mergedFilterRule
    }

    private fun UpdateFilterRuleRequest.toPO(): TFilterRule {
        val userId = SecurityUtils.getUserId()
        val now = LocalDateTime.now()
        val targetProjectIds = if (projectId == SYSTEM_PROJECT_ID) {
            projectIds
        } else {
            null
        }
        return TFilterRule(
            id = null,
            createdBy = userId,
            createdDate = now,
            lastModifiedBy = userId,
            lastModifiedDate = now,
            name = name,
            description = description,
            projectId = projectId,
            projectIds = targetProjectIds,
            repoName = repoName,
            planId = planId,
            fullPath = fullPath,
            packageKey = packageKey,
            packageVersion = packageVersion,
            riskyPackageKeys = riskyPackageKeys,
            riskyPackageVersions = riskyPackageVersions,
            vulIds = vulIds,
            severity = severity,
            licenseNames = licenseNames,
            type = type
        )
    }

    private fun TFilterRule.toDTO(): FilterRule {
        return FilterRule(
            id = id,
            name = name,
            description = description,
            projectId = projectId,
            projectIds = projectIds,
            repoName = repoName,
            planId = planId,
            fullPath = fullPath,
            packageKey = packageKey,
            packageVersion = packageVersion,
            riskyPackageKeys = riskyPackageKeys,
            riskyPackageVersions = riskyPackageVersions,
            vulIds = vulIds,
            severity = severity,
            licenseNames = licenseNames,
            type = type
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FilterRuleServiceImpl::class.java)
    }
}
