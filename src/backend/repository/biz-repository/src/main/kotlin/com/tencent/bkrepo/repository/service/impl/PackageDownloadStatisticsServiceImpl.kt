/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.  
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.repository.model.TDownloadStatistics
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetric
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.service.PackageDownloadStatisticsService
import com.tencent.bkrepo.repository.service.PackageService
import com.tencent.bkrepo.repository.service.RepositoryService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service
class PackageDownloadStatisticsServiceImpl(
    private val repositoryService: RepositoryService,
    private val packageService: PackageService
) : AbstractService(), PackageDownloadStatisticsService {

    override fun add(statisticsAddRequest: DownloadStatisticsAddRequest) {
        with(statisticsAddRequest){
            // 维护 package 和 version 数据
            packageService.addDownloadMetric(projectId, repoName, packageKey, version)
            // 维护 package_download_statistics 表
            val criteria = criteria(projectId, repoName, packageKey, version)
                .and(TDownloadStatistics::name.name).`is`(name)
                .and(TDownloadStatistics::date.name).`is`(LocalDate.now())
            val query = Query(criteria)
            val update = Update().inc(TDownloadStatistics::count.name, 1)
            try {
                mongoTemplate.upsert(query, update, TDownloadStatistics::class.java)
            } catch (exception: DuplicateKeyException) {
                // retry because upsert operation is not atomic
                logger.warn("DuplicateKeyException: " + exception.message.orEmpty())
                mongoTemplate.upsert(query, update, TDownloadStatistics::class.java)
            }
            logger.info("Create artifact download statistics [$statisticsAddRequest] success.")
        }
    }

    override fun query(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String?,
        startDay: LocalDate?,
        endDay: LocalDate?
    ): DownloadStatisticsResponse {
        logger.info("query package download metric request: [projectId: $projectId, repoName: $repoName, packageKey: $packageKey, version: $version, startDay: $startDay, endDay: $endDay].")
        packageKey.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(
            CommonMessageCode.PARAMETER_MISSING,
            "packageKey"
        )
        repositoryService.checkRepository(projectId, repoName)

        val criteria = criteria(projectId, repoName, packageKey, version)
        if (startDay != null && endDay != null) {
            criteria.and(TDownloadStatistics::date.name).lte(endDay).gte(startDay)
        } else {
            startDay?.let { criteria.and(TDownloadStatistics::date.name).gte(it) }
            endDay?.let { criteria.and(TDownloadStatistics::date.name).lte(it) }
        }
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsResponse::count.name)
        )
        val aggregateResult = mongoTemplate.aggregate(aggregation, TDownloadStatistics::class.java, HashMap::class.java)
        val count = getAggregateCount(aggregateResult)
        return DownloadStatisticsResponse(projectId, repoName, packageKey, version, count)
    }

    override fun queryForSpecial(
        projectId: String,
        repoName: String,
        packageKey: String
    ): DownloadStatisticsMetricResponse {
        logger.info("query package download metric for special date period request: [projectId: $projectId, repoName: $repoName, packageKey: $packageKey].")
        packageKey.takeIf { it.isNotBlank() } ?: throw ErrorCodeException(
            CommonMessageCode.PARAMETER_MISSING,
            "packageKey"
        )
        repositoryService.checkRepository(projectId, repoName)
        val today = LocalDate.now()
        val monthCount = queryMonthDownloadCount(projectId, repoName, packageKey, today)
        if (monthCount.toInt() == 0) {
            val statisticsMetricsList = buildStatisticsMetrics(0, 0, 0)
            return DownloadStatisticsMetricResponse(projectId, repoName, packageKey, null, statisticsMetricsList)
        }
        val weekCount = queryWeekDownloadCount(projectId, repoName, packageKey, today)
        val todayCount = queryTodayDownloadCount(projectId, repoName, packageKey, today)
        val statisticsMetricsList = buildStatisticsMetrics(monthCount, weekCount, todayCount)
        return DownloadStatisticsMetricResponse(projectId, repoName, packageKey, null, statisticsMetricsList)
    }

    override fun queryMonthDownloadCount(
        projectId: String,
        repoName: String,
        packageId: String,
        today: LocalDate
    ): Long {
        val firstDayOfThisMonth = today.with(TemporalAdjusters.firstDayOfMonth())
        val lastDayOfThisMonth = today.with(TemporalAdjusters.lastDayOfMonth())
        val monthCriteria =
            criteria(projectId, repoName, packageId)
                .and(TDownloadStatistics::date.name).gte(firstDayOfThisMonth).lte(lastDayOfThisMonth)
        val monthAggregation = Aggregation.newAggregation(
            Aggregation.match(monthCriteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsMetric::count.name)
        )
        val monthResult =
            mongoTemplate.aggregate(monthAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(monthResult)
    }

    override fun queryWeekDownloadCount(
        projectId: String,
        repoName: String,
        packageId: String,
        today: LocalDate
    ): Long {
        val firstDayOfWeek =
            today.with(TemporalAdjusters.ofDateAdjuster { localDate -> localDate.minusDays(localDate.dayOfWeek.value - DayOfWeek.MONDAY.value.toLong()) })
        val lastDayOfWeek =
            today.with(TemporalAdjusters.ofDateAdjuster { localDate -> localDate.plusDays(DayOfWeek.SUNDAY.value.toLong() - localDate.dayOfWeek.value) })
        val weekCriteria =
            criteria(projectId, repoName, packageId).and(TDownloadStatistics::date.name)
                .gte(firstDayOfWeek).lte(lastDayOfWeek)
        val weekAggregation = Aggregation.newAggregation(
            Aggregation.match(weekCriteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsMetric::count.name)
        )
        val weekResult =
            mongoTemplate.aggregate(weekAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(weekResult)
    }

    override fun queryTodayDownloadCount(
        projectId: String,
        repoName: String,
        packageId: String,
        today: LocalDate
    ): Long {
        val todayCriteria =
            criteria(projectId, repoName, packageId).and(TDownloadStatistics::date.name)
                .`is`(today)
        val todayAggregation = Aggregation.newAggregation(
            Aggregation.match(todayCriteria),
            Aggregation.group().sum(TDownloadStatistics::count.name).`as`(DownloadStatisticsMetric::count.name)
        )
        val todayResult =
            mongoTemplate.aggregate(todayAggregation, TDownloadStatistics::class.java, HashMap::class.java)
        return getAggregateCount(todayResult)
    }

    private fun criteria(projectId: String, repoName: String, packageKey: String, version: String? = null): Criteria {
        return Criteria.where(TDownloadStatistics::projectId.name).`is`(projectId)
            .and(TDownloadStatistics::repoName.name).`is`(repoName)
            .and(TDownloadStatistics::key.name).`is`(packageKey)
            .apply {
                version?.let { and(TDownloadStatistics::version.name).`is`(it) }
            }
    }

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(PackageDownloadStatisticsServiceImpl::class.java)

        fun getAggregateCount(aggregateResult: AggregationResults<java.util.HashMap<*, *>>): Long {
            return if (aggregateResult.mappedResults.size > 0) {
                (aggregateResult.mappedResults[0][DownloadStatisticsMetric::count.name] as? Int)?.toLong() ?: 0
            } else 0
        }

        fun buildStatisticsMetrics(
            monthCount: Long,
            weekCount: Long,
            todayCount: Long
        ): List<DownloadStatisticsMetric> {
            return listOf(
                DownloadStatisticsMetric(
                    "month download statistics metric:",
                    monthCount
                ),
                DownloadStatisticsMetric(
                    "week download statistics metric:",
                    weekCount
                ),
                DownloadStatisticsMetric(
                    "today download statistics metric:",
                    todayCount
                )
            )
        }
    }
}