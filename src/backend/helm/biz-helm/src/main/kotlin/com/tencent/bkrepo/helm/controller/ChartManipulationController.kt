package com.tencent.bkrepo.helm.controller

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.CHART_DELETE_VERSION_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_PUSH_PROV_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_PUSH_URL
import com.tencent.bkrepo.helm.pojo.HelmSuccessResponse
import com.tencent.bkrepo.helm.pojo.chart.ChartVersionDeleteRequest
import com.tencent.bkrepo.helm.service.ChartManipulationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ChartManipulationController(
    private val chartManipulationService: ChartManipulationService
) {
    /**
     * helm push
     */
    @PostMapping(HELM_PUSH_URL)
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo,
        artifactFileMap: ArtifactFileMap
    ): HelmSuccessResponse {
        chartManipulationService.upload(artifactInfo, artifactFileMap)
        return HelmSuccessResponse.pushSuccess()
    }

    /**
     * helm push prov
     */
    @PostMapping(HELM_PUSH_PROV_URL)
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadProv(
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo,
        artifactFileMap: ArtifactFileMap
    ): HelmSuccessResponse {
        chartManipulationService.uploadProv(artifactInfo, artifactFileMap)
        return HelmSuccessResponse.pushSuccess()
    }

    /**
     * delete chart version
     */
    @DeleteMapping(CHART_DELETE_VERSION_URL)
    fun deleteVersion(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo,
        @PathVariable name: String,
        @PathVariable version: String
    ): HelmSuccessResponse {
        with(artifactInfo) {
            val chartDeleteRequest = ChartVersionDeleteRequest(projectId, repoName, name, version, userId)
            chartManipulationService.deleteVersion(chartDeleteRequest)
            return HelmSuccessResponse.deleteSuccess()
        }
    }
}
