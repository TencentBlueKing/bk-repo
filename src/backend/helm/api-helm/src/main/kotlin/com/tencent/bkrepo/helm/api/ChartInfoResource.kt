package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.CHARTS_LIST
import io.swagger.annotations.Api
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

@Api("chart search info API")
interface ChartInfoResource {
    @GetMapping(CHARTS_LIST, produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
    fun allChartsList(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo,
        @RequestParam startTime: LocalDateTime?
    )

    @RequestMapping(CHARTS_LIST, method = [RequestMethod.HEAD])
    fun exists(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    )
}
