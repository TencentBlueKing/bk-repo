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

package com.tencent.bkrepo.scanner.dao

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.scanner.model.SubScanTaskDefinition
import com.tencent.bkrepo.scanner.pojo.request.ArtifactPlanRelationRequest
import com.tencent.bkrepo.scanner.pojo.request.PlanArtifactRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.group
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.project
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo

abstract class AbsSubScanTaskDao<E : SubScanTaskDefinition> : SimpleMongoDao<E>() {
    fun findSubScanTasks(request: PlanArtifactRequest, overviewKey: String? = null): Page<E> {
        with(request) {
            val criteria = Criteria
                .where(SubScanTaskDefinition::projectId.name).isEqualTo(projectId)
                .and(SubScanTaskDefinition::parentScanTaskId.name).`is`(parentScanTaskId)

            artifactName?.let {
                criteria.and(SubScanTaskDefinition::artifactName.name).regex(".*$artifactName.*")
            }
            overviewKey?.let {
                criteria.and("${SubScanTaskDefinition::scanResultOverview.name}.$overviewKey").exists(true)
            }
            repoType?.let { criteria.and(SubScanTaskDefinition::repoType.name).isEqualTo(repoType) }
            repoName?.let { criteria.and(SubScanTaskDefinition::repoName.name).isEqualTo(repoName) }
            subScanTaskStatus?.let { criteria.and(SubScanTaskDefinition::status.name).inValues(it) }
            startTime?.let { criteria.and(SubScanTaskDefinition::createdDate.name).gte(it) }
            endTime?.let { criteria.and(SubScanTaskDefinition::finishedDateTime.name).lte(it) }

            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = Query(criteria)
                .with(
                    Sort.by(
                        Sort.Direction.DESC,
                        SubScanTaskDefinition::createdDate.name,
                        SubScanTaskDefinition::repoName.name,
                        SubScanTaskDefinition::fullPath.name,
                        SubScanTaskDefinition::packageKey.name,
                        SubScanTaskDefinition::version.name
                    )
                )
            val count = count(query)
            val records = find(query.with(pageRequest))
            return Page(
                pageNumber = pageRequest.pageNumber + 1,
                pageSize = pageRequest.pageSize,
                totalRecords = count,
                records = records
            )
        }
    }

    @Suppress("SpreadOperator")
    fun findSubScanTasks(request: ArtifactPlanRelationRequest): List<E> {
        with(request) {
            //多个方案扫描过相同项目-仓库-同一个制品
            val groupFields = ArrayList<String>()
            val criteria = Criteria
                .where(SubScanTaskDefinition::projectId.name).isEqualTo(projectId)
                .and(SubScanTaskDefinition::repoName.name).isEqualTo(repoName)
            groupFields.add(SubScanTaskDefinition::projectId.name)
            groupFields.add(SubScanTaskDefinition::repoName.name)
            criteria.and(SubScanTaskDefinition::fullPath.name).`is`(fullPath)
            groupFields.add(SubScanTaskDefinition::fullPath.name)

            val aggregation = newAggregation(
                match(criteria),
                group(*groupFields.toTypedArray()).push(Aggregation.ROOT).`as`(FIELD_ALIAS_ARTIFACT_SUB_SCAN_TASKS),
                project(FIELD_ALIAS_ARTIFACT_SUB_SCAN_TASKS).andExclude(ID)
            )
            val aggregateResult = aggregate(aggregation, artifactPlanRelationAggregateResultClass())
            return artifactPlanRelationAggregateResult(aggregateResult)
        }
    }

    protected abstract fun artifactPlanRelationAggregateResultClass(): Class<*>

    @Suppress("UNCHECKED_CAST")
    private fun artifactPlanRelationAggregateResult(aggregateResult: AggregationResults<*>): List<E> {
        val results = aggregateResult.mappedResults as List<ArtifactPlanRelationAggregateResult<SubScanTaskDefinition>>
        return results
            .asSequence()
            .filter { it.artifactSubScanTasks.isNotEmpty() }
            .map { it.artifactSubScanTasks.maxBy { task -> task.createdDate }!! }
            .toList() as List<E>
    }

    open class ArtifactPlanRelationAggregateResult<T : SubScanTaskDefinition>(
        open val artifactSubScanTasks: List<T>
    )

    companion object {
        private const val FIELD_ALIAS_ARTIFACT_SUB_SCAN_TASKS = "artifactSubScanTasks"
    }
}
