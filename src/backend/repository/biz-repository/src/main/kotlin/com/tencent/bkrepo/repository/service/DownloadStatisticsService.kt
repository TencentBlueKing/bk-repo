package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import java.time.LocalDate

/**
 * 下载统计服务接口
 */
interface DownloadStatisticsService {

    fun add(statisticsAddRequest: DownloadStatisticsAddRequest)

    fun query(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        startDate: LocalDate?,
        endDate: LocalDate?
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
}
