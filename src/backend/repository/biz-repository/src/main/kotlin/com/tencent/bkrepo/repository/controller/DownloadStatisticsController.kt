package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.DownloadStatisticsClient
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.service.DownloadStatisticsService
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class DownloadStatisticsController(
    private val downloadStatisticsService: DownloadStatisticsService
) : DownloadStatisticsClient {

    override fun add(statisticsAddRequest: DownloadStatisticsAddRequest): Response<Void> {
        downloadStatisticsService.add(statisticsAddRequest)
        return ResponseBuilder.success()
    }

    override fun query(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?,
        startDay: LocalDate?,
        endDay: LocalDate?
    ): Response<DownloadStatisticsResponse> {
        return ResponseBuilder.success(
            downloadStatisticsService.query(projectId, repoName, artifact, version, startDay, endDay)
        )
    }

    override fun queryForSpecial(
        projectId: String,
        repoName: String,
        artifact: String,
        version: String?
    ): Response<DownloadStatisticsMetricResponse> {
        return ResponseBuilder.success(
            downloadStatisticsService.queryForSpecial(projectId, repoName, artifact, version)
        )
    }
}
