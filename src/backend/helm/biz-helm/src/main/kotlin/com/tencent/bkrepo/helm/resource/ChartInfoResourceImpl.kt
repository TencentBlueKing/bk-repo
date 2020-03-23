package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.helm.api.ChartInfoResource
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import org.springframework.web.bind.annotation.RestController

@RestController
class ChartInfoResourceImpl : ChartInfoResource {
    override fun allChartsList(artifactInfo: HelmArtifactInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chartsVersion(artifactInfo: HelmArtifactInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chartsDescribe(artifactInfo: HelmArtifactInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}