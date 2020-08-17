package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Criteria
import java.time.LocalDate

/**
 * 下载统计服务
 */
interface DownloadStatisticsService {

    fun add(statisticsAddRequest: DownloadStatisticsAddRequest)

    fun query(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        startDate: LocalDate,
        endDate: LocalDate
    ): DownloadStatisticsResponse

    fun queryForSpecial(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?
    ): DownloadStatisticsMetricResponse

    fun queryMonthDownloadCount(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        today: LocalDate
    ): Int

    fun queryWeekDownloadCount(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        today: LocalDate
    ): Int

    fun queryTodayDownloadCount(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        today: LocalDate
    ): Int

    fun getAggregateCount(aggregateResult: AggregationResults<java.util.HashMap<*, *>>): Int

    fun criteria(projectId: String, repoName: String, artifact: String, version: String?): Criteria
}
