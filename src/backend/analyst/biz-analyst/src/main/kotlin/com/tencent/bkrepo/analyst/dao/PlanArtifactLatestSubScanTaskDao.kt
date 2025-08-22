/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.analyst.model.SubScanTaskDefinition
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.request.PlanCountRequest
import com.tencent.bkrepo.analyst.pojo.request.SubtaskInfoRequest
import com.tencent.bkrepo.analyst.utils.Converter
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.query.model.PageLimit
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.FindAndReplaceOptions
import org.springframework.data.mongodb.core.aggregation.Aggregation.group
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PlanArtifactLatestSubScanTaskDao(
    private val scanPlanDao: ScanPlanDao
) : AbsSubScanTaskDao<TPlanArtifactLatestSubScanTask>() {
    /**
     * 获取指定制品的所有扫描子任务
     *
     * @param projectId projectId
     * @param repoName repoName
     * @param fullPath fullPath
     * @param planExists 是否仅返回有指定扫描方案的任务
     *
     * @return 指定制品使用各扫描方案的最新扫描任务列表
     */
    fun findAll(
        projectId: String,
        repoName: String,
        fullPath: String,
        planExists: Boolean = true
    ): List<TPlanArtifactLatestSubScanTask> {
        val criteria = buildCriteria(projectId, repoName, fullPath)
        if (planExists) {
            criteria.and(SubScanTaskDefinition::planId.name).ne(null)
        }
        return find(Query(criteria))
    }

    /**
     * 获取指定扫描方案的制品最新扫描记录
     *
     * @param request 获取制品最新扫描记录请求
     *
     * @return 扫描方案最新的制品扫描结果
     */
    fun findBy(request: PlanCountRequest): List<TPlanArtifactLatestSubScanTask> {
        with(request) {
            val criteria = Criteria
                .where(SubScanTaskDefinition::projectId.name).isEqualTo(projectId)
                .and(SubScanTaskDefinition::planId.name).isEqualTo(id)
                .and(SubScanTaskDefinition::createdDate.name).gte(startDateTime!!).lte(endDateTime!!)
            return find(Query(criteria))
        }
    }

    /**
     * 获取指定扫描方案的制品最新扫描记录
     *
     * @param request 获取制品最新扫描记录请求
     *
     * @return 扫描方案最新的制品扫描结果
     */
    fun planLatestRecords(request: SubtaskInfoRequest): List<TPlanArtifactLatestSubScanTask> {
        with(request) {
            val criteria = Criteria.where(TPlanArtifactLatestSubScanTask::projectId.name).isEqualTo(projectId)
            id?.let { criteria.and(TPlanArtifactLatestSubScanTask::planId.name).isEqualTo(id) }
            subScanTaskStatus?.let { criteria.and(TPlanArtifactLatestSubScanTask::status.name).inValues(it) }
            if (startDateTime != null && endDateTime != null) {
                criteria.and(TPlanArtifactLatestSubScanTask::createdDate.name).gte(startDateTime!!).lte(endDateTime!!)
            }
            qualityRedLine?.let {
                criteria.and(TPlanArtifactLatestSubScanTask::qualityRedLine.name).isEqualTo(qualityRedLine)
            }
            unQuality?.let {
                criteria.and(TPlanArtifactLatestSubScanTask::qualityRedLine.name).nin(listOf(true, false))
            }

            val query = Query(criteria).with(
                Sort.by(
                    Sort.Direction.DESC,
                    TPlanArtifactLatestSubScanTask::lastModifiedDate.name,
                    TPlanArtifactLatestSubScanTask::repoName.name,
                    TPlanArtifactLatestSubScanTask::fullPath.name
                )
            )
            return find(query)
        }
    }

    /**
     * 获取扫描方案制品扫描结果
     *
     * @param planId 扫描方案id
     * @param pageLimit 分页参数
     *
     * @return 扫描方案最新的制品扫描结果
     */
    fun pageByPlanId(planId: String, pageLimit: PageLimit? = null): List<TPlanArtifactLatestSubScanTask> {
        val query = Query(TPlanArtifactLatestSubScanTask::planId.isEqualTo(planId))
        if (pageLimit != null) {
            val pageRequest = Pages.ofRequest(pageLimit.getNormalizedPageNumber(), pageLimit.getNormalizedPageSize())
            query.with(pageRequest).with(Sort.by(ID).ascending())
        }
        return find(query)
    }

    /**
     * [subtasks] 中的记录存在时则替换，不存在时插入
     */
    fun replace(subtasks: List<TPlanArtifactLatestSubScanTask>) {
        if (subtasks.isEmpty()) {
            return
        }

        updateScanPlanResultOverview(subtasks)

        val bulkOps = determineMongoTemplate()
            .bulkOps(BulkOperations.BulkMode.UNORDERED, classType, determineCollectionName())

        val options = FindAndReplaceOptions.options().upsert()
        subtasks.forEach {
            val query = Query(buildCriteria(it.projectId, it.repoName, it.fullPath, it.planId))
            bulkOps.replaceOne(query, it, options)
        }
        bulkOps.execute()
    }

    /**
     * 更新任务状态
     *
     * @param latestSubScanTaskId 扫描方案制品最新一次扫描子任务id
     * @param subtaskScanStatus 新状态
     * @param now 当前时间
     * @param overview 扫描结果预览信息，更新为结束状态时存在
     * @param modifiedBy 状态修改人
     *
     * @return 更新结果
     */
    fun updateStatus(
        latestSubScanTaskId: String,
        subtaskScanStatus: String,
        overview: Map<String, Any?>? = null,
        modifiedBy: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
        qualityPass: Boolean? = null
    ): UpdateResult {
        val criteria = Criteria
            .where(TPlanArtifactLatestSubScanTask::latestSubScanTaskId.name).isEqualTo(latestSubScanTaskId)

        val update = Update
            .update(TPlanArtifactLatestSubScanTask::lastModifiedDate.name, now)
            .set(TPlanArtifactLatestSubScanTask::status.name, subtaskScanStatus)
        modifiedBy?.let { update.set(TPlanArtifactLatestSubScanTask::lastModifiedBy.name, it) }
        qualityPass?.let { update.set(TPlanArtifactLatestSubScanTask::qualityRedLine.name, it) }

        // 更新为正在扫描的状态时更新
        if (subtaskScanStatus == SubScanTaskStatus.EXECUTING.name) {
            update.set(TSubScanTask::startDateTime.name, now)
        }

        // 更新为扫描结束时更新
        if (SubScanTaskStatus.finishedStatus(subtaskScanStatus)) {
            update.set(TPlanArtifactLatestSubScanTask::scanResultOverview.name, overview?.let { Converter.convert(it) })
            update.set(TPlanArtifactLatestSubScanTask::finishedDateTime.name, now)
        }

        return updateFirst(Query(criteria), update)
    }

    /**
     * 批量更新任务状态
     */
    fun updateStatus(
        latestSubScanTaskIds: List<String>,
        subtaskScanStatus: String
    ): UpdateResult {
        val criteria =
            Criteria.where(TPlanArtifactLatestSubScanTask::latestSubScanTaskId.name).inValues(latestSubScanTaskIds)
        val update = Update()
            .set(TSubScanTask::lastModifiedDate.name, LocalDateTime.now())
            .set(TSubScanTask::status.name, subtaskScanStatus)
        return updateMulti(Query(criteria), update)
    }

    /**
     * 获取扫描方案累计扫描的制品数
     */
    fun planArtifactCount(planId: String): Long {
        val query = Query(TPlanArtifactLatestSubScanTask::planId.isEqualTo(planId))
        return count(query)
    }

    fun planArtifactCount(planIds: List<String>): Map<String, Long> {
        val countFieldName = "count"
        val criteria = TPlanArtifactLatestSubScanTask::planId.inValues(planIds)
        val aggregate = newAggregation(
            match(criteria),
            group(TPlanArtifactLatestSubScanTask::planId.name).count().`as`(countFieldName)
        )
        val result = aggregate(aggregate, HashMap::class.java).mappedResults
        val planArtifactCountMap = HashMap<String, Long>(result.size)

        result.forEach {
            planArtifactCountMap[it[ID] as String] = (it[countFieldName] as Number).toLong()
        }

        return planArtifactCountMap
    }

    fun deleteByLatestSubtasks(latestSubScanTaskIds: List<String>): List<TPlanArtifactLatestSubScanTask> {
        val query = Query(TPlanArtifactLatestSubScanTask::latestSubScanTaskId.inValues(latestSubScanTaskIds))
        return determineMongoTemplate().findAllAndRemove(query, TPlanArtifactLatestSubScanTask::class.java)
    }

    /**
     * 更新扫描方案预览结果
     */
    @Suppress("SpreadOperator")
    private fun updateScanPlanResultOverview(subtasks: List<TPlanArtifactLatestSubScanTask>) {
        // 查找扫描方案对应制品的记录
        val criteriaList = ArrayList<Criteria>(subtasks.size)
        subtasks.forEach {
            if (it.planId != null) {
                criteriaList.add(buildCriteria(it.projectId, it.repoName, it.fullPath, it.planId))
            }
        }

        if (criteriaList.isEmpty()) {
            return
        }

        val criteria = Criteria().orOperator(*criteriaList.toTypedArray())
        val oldSubtasks = find(Query(criteria))
        if (oldSubtasks.isEmpty()) {
            return
        }

        // 减少旧子任务对应的扫描方案预览值
        scanPlanDao.decrementScanResultOverview(oldSubtasks)
    }

    private fun buildCriteria(projectId: String, repoName: String, fullPath: String, planId: String?): Criteria {
        return buildCriteria(projectId, repoName, fullPath)
            .and(TPlanArtifactLatestSubScanTask::planId.name).isEqualTo(planId)
    }

    private fun buildCriteria(projectId: String, repoName: String, fullPath: String): Criteria {
        return Criteria
            .where(SubScanTaskDefinition::projectId.name).isEqualTo(projectId)
            .and(SubScanTaskDefinition::repoName.name).isEqualTo(repoName)
            .and(SubScanTaskDefinition::fullPath.name).isEqualTo(fullPath)
    }
}
