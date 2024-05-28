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

package com.tencent.bkrepo.job.batch.task.usage

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ProjectDailyAvgMetricsJobProperties
import com.tencent.bkrepo.job.pojo.project.TProjectMetricsDailyAvgRecord
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 根据每日用量采点数据生成每日平均用量
 */
@Component
@EnableConfigurationProperties(ProjectDailyAvgMetricsJobProperties::class)
class ProjectDailyAvgMetricsJob(
    val properties: ProjectDailyAvgMetricsJobProperties,
    private val mongoTemplate: MongoTemplate
) : DefaultContextJob(properties) {

    override fun doStart0(jobContext: JobContext) {
        val currentDate = LocalDate.now().atStartOfDay()
        val criteria = Criteria.where(ProjectMetricsDailyRecord::createdDate.name).lte(currentDate)
            .gte(currentDate.minusDays(1))
        val query = Query(criteria)
        val data = mongoTemplate.findDistinct(
            query, ProjectMetricsDailyRecord::projectId.name,
            COLLECTION_NAME_PROJECT_METRICS_DAILY_RECORD, String::class.java
        )
        data.forEach {
            val criteria = Criteria.where(PROJECT).isEqualTo(it)
                .and(ProjectMetricsDailyRecord::createdDate.name).lte(currentDate)
                .gte(currentDate.minusDays(1))
            val query = Query.query(criteria)
            var capSize = 0L
            var count = 0
            mongoTemplate.find(
                query, ProjectMetricsDailyRecord::class.java,
                COLLECTION_NAME_PROJECT_METRICS_DAILY_RECORD
            ).forEach {
                capSize += it.capSize
                count++
            }
            handleProjectDailyAvgRecord(
                projectId = it,
                capSize = capSize,
                count = count,
                currentDate = currentDate
            )
        }
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    private fun handleProjectDailyAvgRecord(
        projectId: String, capSize: Long, count: Int, currentDate: LocalDateTime
    ) {
        val query = Query(where(ProjectInfo::name).isEqualTo(projectId))
        val projectInfo = mongoTemplate.find(query, ProjectInfo::class.java, COLLECTION_NAME_PROJECT)
            .firstOrNull() ?: return
        val usage = (capSize.toDouble() / (count * 1024 * 1024 * 1024)).toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP).toDouble()
        storeDailyAvgRecord(projectInfo, currentDate, usage)
    }

    private fun storeDailyAvgRecord(
        projectInfo: ProjectInfo,
        currentDate: LocalDateTime,
        usage: Double,
    ) {
        val productId = projectInfo.metadata.firstOrNull { it.key == ProjectMetadata.KEY_PRODUCT_ID }?.value as? String
            ?: StringPool.EMPTY
        val bgId = projectInfo.metadata.firstOrNull { it.key == ProjectMetadata.KEY_BG_ID }?.value as? String
            ?: StringPool.EMPTY
        val dailyRecord = TProjectMetricsDailyAvgRecord(
            projectId = projectInfo.name,
            costDate = convertToCostDate(currentDate),
            name = projectInfo.displayName,
            usage = usage,
            bgName = projectInfo.metadata.firstOrNull { it.key == ProjectMetadata.KEY_BG_NAME }?.value as? String
                ?: StringPool.EMPTY,
            flag = covertToFlag(bgId, productId),
            costDateDay = currentDate.minusDays(1).format(
                DateTimeFormatter.ofPattern("yyyyMMdd")
            ),
        )
        val query = Query(
            where(TProjectMetricsDailyAvgRecord::projectId).isEqualTo(dailyRecord.projectId)
                .and(TProjectMetricsDailyAvgRecord::costDate.name).isEqualTo(dailyRecord.costDate)
                .and(TProjectMetricsDailyAvgRecord::costDateDay.name).isEqualTo(dailyRecord.costDateDay)
        )
        val update = buildUpdateRecord(dailyRecord)
        mongoTemplate.upsert(query, update, COLLECTION_NAME_PROJECT_METRICS_DAILY_AVG_RECORD)
    }

    private fun buildUpdateRecord(dailyRecord: TProjectMetricsDailyAvgRecord): Update {
        val update = Update()
        update.set(TProjectMetricsDailyAvgRecord::name.name, dailyRecord.name)
        update.set(TProjectMetricsDailyAvgRecord::usage.name, dailyRecord.usage)
        update.set(TProjectMetricsDailyAvgRecord::bgName.name, dailyRecord.bgName)
        update.set(TProjectMetricsDailyAvgRecord::flag.name, dailyRecord.flag)
        return update
    }

    private fun covertToFlag(
        bgId: String,
        productId: String
    ): Boolean {
        return if (properties.bgIds.isEmpty()) {
            bgId.isNotBlank() && productId.isNotBlank()
        } else {
            properties.bgIds.contains(bgId) && bgId.isNotBlank() && productId.isNotBlank()
        }
    }

    private fun convertToCostDate(currentDate: LocalDateTime): String {
        val day = currentDate.minusDays(1).dayOfMonth
        val minusMonth = if (day >= properties.monthStartDay) {
            -1
        } else {
            1
        }
        return currentDate.minusMonths(minusMonth.toLong()).format(
            DateTimeFormatter.ofPattern("yyyyMM")
        )
    }

    data class ProjectInfo(
        val name: String,
        val displayName: String,
        val metadata: List<ProjectMetadata> = emptyList(),
    )

    data class ProjectMetricsDailyRecord(
        var projectId: String,
        var capSize: Long,
        val createdDate: LocalDateTime,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectDailyAvgMetricsJob::class.java)
        private const val COLLECTION_NAME_PROJECT = "project"
        private const val COLLECTION_NAME_PROJECT_METRICS_DAILY_RECORD = "project_metrics_daily_record"
        private const val COLLECTION_NAME_PROJECT_METRICS_DAILY_AVG_RECORD = "project_metrics_daily_avg_record"
    }
}
