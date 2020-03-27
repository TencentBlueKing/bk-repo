package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.helm.api.ChartRepositoryResource
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ChartRepositoryResourceImpl : ChartRepositoryResource {

    @Autowired
    private lateinit var chartRepositoryService: ChartRepositoryService

    override fun getIndexYaml(artifactInfo: HelmArtifactInfo) {
        chartRepositoryService.getIndexYaml(artifactInfo)
    }

    override fun installTgz(artifactInfo: HelmArtifactInfo) {
        chartRepositoryService.installTgz(artifactInfo)
    }

}