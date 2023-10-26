/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.dao

import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import com.tencent.bkrepo.analyst.model.SubScanTaskDefinition
import com.tencent.bkrepo.analyst.pojo.request.SubtaskInfoRequest
import com.tencent.bkrepo.analyst.utils.Converter
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo

abstract class AbsSubScanTaskDao<E : SubScanTaskDefinition> : ScannerSimpleMongoDao<E>() {
    fun find(projectId: String, subtaskId: String): E? {
        val criteria = Criteria
            .where(SubScanTaskDefinition::projectId.name).isEqualTo(projectId)
            .and(ID).isEqualTo(subtaskId)
        return findOne(Query(criteria))
    }

    /**
     * 分页获取指定扫描方案的制品最新扫描记录
     *
     * @param request 获取制品最新扫描记录请求
     *
     * @return 扫描方案最新的制品扫描结果
     */
    fun pageBy(request: SubtaskInfoRequest): Page<E> {
        with(request) {
            val criteria = Criteria.where(SubScanTaskDefinition::projectId.name).isEqualTo(projectId)
            parentScanTaskId?.let { criteria.and(SubScanTaskDefinition::parentScanTaskId.name).isEqualTo(it) }
            id?.let { criteria.and(SubScanTaskDefinition::planId.name).isEqualTo(id) }
            name?.let {
                criteria.and(SubScanTaskDefinition::artifactName.name).regex(".*$name.*")
            }
            highestLeakLevel?.let { addHighestVulnerabilityLevel(it, criteria) }
            repoType?.let { criteria.and(SubScanTaskDefinition::repoType.name).isEqualTo(repoType) }
            repoName?.let { criteria.and(SubScanTaskDefinition::repoName.name).isEqualTo(repoName) }
            subScanTaskStatus?.let { criteria.and(SubScanTaskDefinition::status.name).inValues(it) }
            if (startTime != null && endTime != null) {
                criteria.and(SubScanTaskDefinition::createdDate.name).gte(startDateTime!!).lte(endDateTime!!)
            }
            qualityRedLine?.let { criteria.and(SubScanTaskDefinition::qualityRedLine.name).isEqualTo(qualityRedLine) }
            unQuality?.let { criteria.and(SubScanTaskDefinition::qualityRedLine.name).nin(listOf(true, false)) }

            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = Query(criteria)
                .with(
                    Sort.by(
                        Sort.Direction.DESC,
                        SubScanTaskDefinition::lastModifiedDate.name,
                        SubScanTaskDefinition::repoName.name,
                        SubScanTaskDefinition::fullPath.name
                    )
                )
            val count = count(query)
            val records = find(query.with(pageRequest))
            return Page(pageNumber, pageSize, count, records)
        }
    }

    fun deleteByParentTaskId(parentTaskId: String): DeleteResult {
        val query = Query(SubScanTaskDefinition::parentScanTaskId.isEqualTo(parentTaskId))
        return remove(query)
    }

    private fun addHighestVulnerabilityLevel(level: String, criteria: Criteria): Criteria {
        Level.values().forEach {
            val isHighest = level == it.levelName
            criteria.and(resultOverviewKey(it.levelName)).exists(isHighest)
            if (isHighest) {
                return criteria
            }
        }
        return criteria
    }

    private fun resultOverviewKey(level: String): String {
        val overviewKey = Converter.getCveOverviewKey(level)
        return "${SubScanTaskDefinition::scanResultOverview.name}.$overviewKey"
    }
}
