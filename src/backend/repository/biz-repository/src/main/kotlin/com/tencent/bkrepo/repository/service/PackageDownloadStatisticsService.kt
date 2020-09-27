package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import java.time.LocalDate

interface PackageDownloadStatisticsService {

    fun add(statisticsAddRequest: DownloadStatisticsAddRequest)

    fun query(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String?,
        startDay: LocalDate?,
        endDay: LocalDate?
    ): DownloadStatisticsResponse

    fun queryForSpecial(
        projectId: String,
        repoName: String,
        packageKey: String
    ): DownloadStatisticsMetricResponse

    fun queryMonthDownloadCount(
        projectId: String,
        repoName: String,
        packageId: String,
        today: LocalDate
    ): Long

    fun queryWeekDownloadCount(
        projectId: String,
        repoName: String,
        packageId: String,
        today: LocalDate
    ): Long

    fun queryTodayDownloadCount(
        projectId: String,
        repoName: String,
        packageId: String,
        today: LocalDate
    ): Long
}