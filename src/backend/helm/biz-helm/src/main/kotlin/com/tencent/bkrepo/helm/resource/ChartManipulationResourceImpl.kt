package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.helm.api.ChartManipulationResource
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.service.ChartManipulationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ChartManipulationResourceImpl : ChartManipulationResource {

    @Autowired
    private lateinit var chartManipulationService: ChartManipulationService

    override fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): Map<String, Any> {
        return chartManipulationService.upload(artifactInfo, artifactFileMap)
    }
}
