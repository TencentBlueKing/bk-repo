package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.helm.api.ChartInfoResource
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.service.ChartInfoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ChartInfoResourceImpl : ChartInfoResource {

    @Autowired
    private lateinit var chartInfoService: ChartInfoService

    override fun allChartsList(artifactInfo: HelmArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        response.writer.print(chartInfoService.allChartsList(artifactInfo))
    }

    override fun exists(artifactInfo: HelmArtifactInfo) {
        return chartInfoService.isExists(artifactInfo)
    }
}
