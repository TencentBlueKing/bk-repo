package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import io.swagger.annotations.Api
import org.springframework.web.bind.annotation.GetMapping

@Api("chart search info API")
interface ChartInfoResource {
    @GetMapping(HelmArtifactInfo.CHARTS_LIST)
    fun allChartsList(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    )

    @GetMapping(HelmArtifactInfo.CHARTS_VERSION)
    fun chartsVersion(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    )

    @GetMapping(HelmArtifactInfo.CHARTS_DESCRIBE)
    fun chartsDescribe(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    )


}