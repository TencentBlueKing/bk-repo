package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.DownloadStatisticsResource
import com.tencent.bkrepo.repository.pojo.download.count.DownloadStatisticsForSpecialDateInfoResponse
import com.tencent.bkrepo.repository.pojo.download.count.DownloadStatisticsResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadStatisticsCreateRequest
import com.tencent.bkrepo.repository.service.DownloadStatisticsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class DownloadStatisticsResourceImpl @Autowired constructor(
    private val downloadStatisticsService: DownloadStatisticsService
) : DownloadStatisticsResource {

    override fun add(statisticsCreateRequest: DownloadStatisticsCreateRequest): Response<Void> {
        downloadStatisticsService.add(statisticsCreateRequest)
        return ResponseBuilder.success()
    }

    override fun query(projectId: String, repoName: String, artifact: String, version: String?, startDay: LocalDate, endDay: LocalDate): Response<DownloadStatisticsResponseInfo> {
        return ResponseBuilder.success(downloadStatisticsService.query(projectId, repoName, artifact, version, startDay, endDay))
    }

    override fun queryForSpecial(projectId: String, repoName: String, artifact: String, version: String?): Response<DownloadStatisticsForSpecialDateInfoResponse> {
        return ResponseBuilder.success(downloadStatisticsService.queryForSpecial(projectId, repoName, artifact, version))
    }
}
