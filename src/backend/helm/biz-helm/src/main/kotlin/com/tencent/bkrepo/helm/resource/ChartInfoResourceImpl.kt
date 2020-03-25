package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.helm.api.ChartInfoResource
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.service.ChartInfoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ChartInfoResourceImpl : ChartInfoResource {

    @Autowired
    private lateinit var chartInfoService: ChartInfoService

    override fun allChartsList(artifactInfo: HelmArtifactInfo): String {
        return chartInfoService.allChartsList(artifactInfo)
    }

    override fun exists(artifactInfo: HelmArtifactInfo) {
        return chartInfoService.isExists(artifactInfo)
    }
}