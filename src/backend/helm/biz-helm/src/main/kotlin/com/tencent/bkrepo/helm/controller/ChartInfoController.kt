package com.tencent.bkrepo.helm.controller

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.CHARTS_LIST
import com.tencent.bkrepo.helm.service.ChartInfoService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class ChartInfoController(
    private val chartInfoService: ChartInfoService
) {
    @GetMapping(CHARTS_LIST, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun allChartsList(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo,
        @RequestParam startTime: LocalDateTime?
    ): Map<String, Any> {
        return chartInfoService.allChartsList(artifactInfo, startTime)
    }

    @RequestMapping(CHARTS_LIST, method = [RequestMethod.HEAD])
    fun exists(
        @ArtifactPathVariable
        artifactInfo: HelmArtifactInfo
    ) {
        chartInfoService.isExists(artifactInfo)
    }
}
