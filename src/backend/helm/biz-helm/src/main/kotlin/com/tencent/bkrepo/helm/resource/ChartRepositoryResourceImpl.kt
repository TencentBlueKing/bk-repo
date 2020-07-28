package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.helm.api.ChartRepositoryResource
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

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

    override fun installProv(artifactInfo: HelmArtifactInfo) {
        chartRepositoryService.installTgz(artifactInfo)
    }

    override fun regenerateIndexYaml(artifactInfo: HelmArtifactInfo): Response<Void> {
        chartRepositoryService.regenerateIndexYaml(artifactInfo)
        return ResponseBuilder.success()
    }

    override fun batchInstallTgz(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime) {
        chartRepositoryService.batchInstallTgz(artifactInfo, startTime)
    }
}
