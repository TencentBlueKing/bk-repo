package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.helm.api.ChartManipulationResource
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.HelmSuccessResponse
import com.tencent.bkrepo.helm.service.ChartManipulationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ChartManipulationResourceImpl : ChartManipulationResource {

    @Autowired
    private lateinit var chartManipulationService: ChartManipulationService

    override fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): HelmSuccessResponse {
        return chartManipulationService.upload(artifactInfo, artifactFileMap)
    }

    override fun uploadProv(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): HelmSuccessResponse {
        return chartManipulationService.uploadProv(artifactInfo, artifactFileMap)
    }

    override fun delete(artifactInfo: HelmArtifactInfo): HelmSuccessResponse {
        return chartManipulationService.delete(artifactInfo)
    }
}
