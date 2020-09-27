package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.PackageDownloadStatisticsClient
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.service.PackageDownloadStatisticsService
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class PackageDownloadStatisticsController(
    private val packageDownloadStatisticsService: PackageDownloadStatisticsService
): PackageDownloadStatisticsClient {

    override fun add(statisticsAddRequest: DownloadStatisticsAddRequest): Response<Void> {
        packageDownloadStatisticsService.add(statisticsAddRequest)
        return ResponseBuilder.success()
    }

    override fun query(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String?,
        startDay: LocalDate?,
        endDay: LocalDate?
    ): Response<DownloadStatisticsResponse> {
        return ResponseBuilder.success(
            packageDownloadStatisticsService.query(projectId, repoName, packageKey, version, startDay, endDay)
        )
    }

    override fun queryForSpecial(
        projectId: String,
        repoName: String,
        packageKey: String
    ): Response<DownloadStatisticsMetricResponse> {
        return ResponseBuilder.success(
            packageDownloadStatisticsService.queryForSpecial(projectId, repoName, packageKey)
        )
    }
}