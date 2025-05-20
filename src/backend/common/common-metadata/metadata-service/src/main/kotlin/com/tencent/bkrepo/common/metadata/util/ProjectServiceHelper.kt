/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.constant.CLOSED_SOURCE_PREFIX
import com.tencent.bkrepo.common.api.constant.CODE_PROJECT_PREFIX
import com.tencent.bkrepo.common.api.constant.TENANT_ID
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.metadata.model.TProject
import com.tencent.bkrepo.common.metadata.model.TProjectMetrics
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectListOption
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import com.tencent.bkrepo.repository.pojo.project.ProjectMetricsInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectSearchOption
import com.tencent.bkrepo.repository.pojo.project.ProjectUpdateRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.elemMatch
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.regex
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object ProjectServiceHelper {
    const val PROJECT_NAME_PATTERN = "[a-zA-Z_][a-zA-Z0-9\\-_]{1,99}"
    const val DISPLAY_NAME_LENGTH_MIN = 2
    const val DISPLAY_NAME_LENGTH_MAX = 100

    fun convert(tProject: TProject?): ProjectInfo? {
        return convert(tProject, false)
    }

    fun convert(tProject: TProject?, rbacFlag: Boolean): ProjectInfo? {
        return tProject?.let {
            ProjectInfo(
                name = it.name,
                displayName = it.displayName,
                description = it.description,
                createdBy = it.createdBy,
                createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = it.lastModifiedBy,
                lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                metadata = it.metadata,
                credentialsKey = it.credentialsKey,
                rbacFlag = rbacFlag,
                tenantId = it.tenantId
            )
        }
    }

    fun convert(tProjectMetrics: TProjectMetrics?): ProjectMetricsInfo? {
        return tProjectMetrics?.let {
            ProjectMetricsInfo(
                projectId = it.projectId,
                nodeNum = it.nodeNum,
                capSize = it.capSize,
                repoMetrics = it.repoMetrics,
                createdDate = it.createdDate
            )
        }
    }

    fun ProjectCreateRequest.buildProject(tenantId: String?): TProject {
        if (tenantId != null) {
            return TProject(
                name = "$tenantId.$name",
                displayName = displayName,
                description = description.orEmpty(),
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
                metadata = metadata,
                credentialsKey = credentialsKey,
                projectCode = name,
                tenantId = tenantId
            )
        } else {
            return TProject(
                name = name,
                displayName = displayName,
                description = description.orEmpty(),
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now(),
                metadata = metadata,
                credentialsKey = credentialsKey,
            )
        }
    }

    fun checkPropertyAndDirection(option: ProjectListOption) {
        Preconditions.checkArgument(
            option.sortProperty?.none { !TProject::class.java.declaredFields.map { f -> f.name }.contains(it) },
            "sortProperty"
        )
        Preconditions.checkArgument(
            option.direction?.none { it != Sort.Direction.DESC.name && it != Sort.Direction.ASC.name },
            "direction"
        )
    }

    fun isProjectEnabled(project: TProject): Boolean {
        val enabled = project.metadata.firstOrNull {
            it.key == ProjectMetadata.KEY_ENABLED
        }?.value as? Boolean ?: true
        return enabled
    }

    fun getTenantId(): String? {
        return HttpContextHolder.getRequestOrNull()?.getHeader(TENANT_ID)
    }

    fun buildListQuery(): Query {
        val criteria1 = TProject::name.regex("^$CLOSED_SOURCE_PREFIX")
        val criteria2 = TProject::name.regex("^$CODE_PROJECT_PREFIX")
        val query = Query().addCriteria(Criteria().norOperator(criteria1, criteria2)).limit(10000)
        return query
    }

    fun ProjectSearchOption.buildSearchQuery(pageRequest: PageRequest): Query {
        val query = Query().with(pageRequest)

        if (!namePrefix.isNullOrEmpty()) {
            val criteria = TProject::name.regex("^${EscapeUtils.escapeRegex(namePrefix!!)}", "i")
            query.addCriteria(criteria)
        }
        return query
    }

    fun buildListQuery(
        names: List<String>,
        option: ProjectListOption?
    ): Query {
        val tenantId = getTenantId()
        val query = Query.query(
            where(TProject::name).`in`(names)
                .apply { option?.displayNames?.let { and(TProject::displayName).`in`(option.displayNames!!) } }
                .apply { tenantId?.let { and(TProject::tenantId).`is`(tenantId) } }
        )
        if (option?.sortProperty?.isNotEmpty() == true) {
            checkPropertyAndDirection(option)
            option.direction?.zip(option.sortProperty!!)?.forEach {
                query.with(Sort.by(Sort.Direction.valueOf(it.first), it.second))
            }
        }
        return query
    }

    fun buildRangeQuery(request: ProjectRangeQueryRequest): Query {
        var criteria = if (request.projectIds.isEmpty()) {
            Criteria()
        } else {
            TProject::name.inValues(request.projectIds)
        }
        if (request.projectMetadata.isNotEmpty()) {
            val metadataCriteria = request.projectMetadata.map {
                TProject::metadata.elemMatch(
                    ProjectMetadata::key.isEqualTo(it.key).and(ProjectMetadata::value.name).isEqualTo(it.value)
                )
            }
            criteria = Criteria().andOperator(metadataCriteria + criteria)
        }
        val query = Query(criteria)
        return query
    }

    fun buildUpdate(request: ProjectUpdateRequest): Update {
        val update = Update().apply {
            request.displayName?.let { this.set(TProject::displayName.name, it) }
            request.description?.let { this.set(TProject::description.name, it) }
        }
        if (request.credentialsKey != null) {
            update.set(TProject::credentialsKey.name, request.credentialsKey)
        } else if (request.useDefaultCredentialsKey == true) {
            update.set(TProject::credentialsKey.name, null)
        }

        if (request.metadata.isNotEmpty()) {
            // 直接使用request的metadata，不存在于request的metadata会被删除，存在的会被覆盖
            update.set(TProject::metadata.name, request.metadata)
        }
        return update
    }

    fun ProjectCreateRequest.validate() {
        if (!Pattern.matches(PROJECT_NAME_PATTERN, name)) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, this::name.name)
        }
        if (displayName.isBlank() ||
            displayName.length < DISPLAY_NAME_LENGTH_MIN ||
            displayName.length > DISPLAY_NAME_LENGTH_MAX
        ) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, this::displayName.name)
        }
    }
}
