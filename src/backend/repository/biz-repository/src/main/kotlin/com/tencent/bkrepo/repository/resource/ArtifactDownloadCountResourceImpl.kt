package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.ArtifactDownloadCountResource
import com.tencent.bkrepo.repository.pojo.download.count.CountResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.CountWithSpecialDayInfoResponse
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import com.tencent.bkrepo.repository.service.ArtifactDownloadCountService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class ArtifactDownloadCountResourceImpl @Autowired constructor(
    private val artifactDownloadCountService: ArtifactDownloadCountService
) : ArtifactDownloadCountResource {

    override fun create(countCreateRequest: DownloadCountCreateRequest): Response<Void> {
        return artifactDownloadCountService.create(countCreateRequest)
    }

    override fun find(projectId: String, repoName: String, artifact: String, version: String?, startDay: LocalDate, endDay: LocalDate): Response<CountResponseInfo> {
        return ResponseBuilder.success(artifactDownloadCountService.find(projectId, repoName, artifact, version, startDay, endDay))
    }

    override fun query(projectId: String, repoName: String, artifact: String, version: String?): Response<CountWithSpecialDayInfoResponse> {
        return ResponseBuilder.success(artifactDownloadCountService.query(projectId, repoName, artifact, version))
    }
}
