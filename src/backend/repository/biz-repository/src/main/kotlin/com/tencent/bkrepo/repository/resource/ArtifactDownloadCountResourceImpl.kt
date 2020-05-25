package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.ArtifactDownloadCountResource
import com.tencent.bkrepo.repository.pojo.download.count.CountResponseInfo
import com.tencent.bkrepo.repository.pojo.download.count.CountWithSpecialDayInfoResponse
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountQueryRequest
import com.tencent.bkrepo.repository.pojo.download.count.service.QueryWithSpecialDayRequest
import com.tencent.bkrepo.repository.service.ArtifactDownloadCountService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ArtifactDownloadCountResourceImpl @Autowired constructor(
    private val artifactDownloadCountService: ArtifactDownloadCountService
): ArtifactDownloadCountResource {

    override fun create(countCreateRequest: DownloadCountCreateRequest): Response<Void> {
        return artifactDownloadCountService.create(countCreateRequest)
    }

    override fun find(countQueryRequest: DownloadCountQueryRequest): Response<CountResponseInfo> {
        return artifactDownloadCountService.find(countQueryRequest)
    }

}