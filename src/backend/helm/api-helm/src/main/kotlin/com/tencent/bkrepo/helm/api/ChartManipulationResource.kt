package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.CHART_DELETE_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_PUSH_PROV_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_PUSH_URL
import com.tencent.bkrepo.helm.pojo.HelmSuccessResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Api("chart 相关操作API")
interface ChartManipulationResource {
    @ApiOperation("helm push")
    @PostMapping(HELM_PUSH_URL)
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo,
        artifactFileMap: ArtifactFileMap
    ): HelmSuccessResponse

    @ApiOperation("helm push prov")
    @PostMapping(HELM_PUSH_PROV_URL)
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadProv(
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo,
        artifactFileMap: ArtifactFileMap
    ): HelmSuccessResponse

    @ApiOperation("delete chart")
    @DeleteMapping(CHART_DELETE_URL)
    fun delete(
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo
    ): HelmSuccessResponse
}
