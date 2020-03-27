package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import io.swagger.annotations.Api
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@Api("chart search info API")
interface ChartInfoResource {
    @GetMapping(HelmArtifactInfo.CHARTS_LIST,
    produces = [MediaType.APPLICATION_JSON_UTF8_VALUE]
    )
    fun allChartsList(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    )

    @RequestMapping(HelmArtifactInfo.CHARTS_LIST , method = [RequestMethod.HEAD])
    fun exists(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    )
}