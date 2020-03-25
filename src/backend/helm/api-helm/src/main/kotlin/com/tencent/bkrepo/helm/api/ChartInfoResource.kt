package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import io.swagger.annotations.Api
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@Api("chart search info API")
interface ChartInfoResource {
    @GetMapping(HelmArtifactInfo.CHARTS_LIST)
    fun allChartsList(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    ): String

    @RequestMapping(HelmArtifactInfo.CHARTS_LIST , method = [RequestMethod.HEAD])
    fun exists(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    )
}