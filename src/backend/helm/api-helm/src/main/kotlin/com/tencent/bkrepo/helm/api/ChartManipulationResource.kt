package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.PostMapping

@Api("chart 相关操作API")
interface ChartManipulationResource {
    @ApiOperation("helm push")
    @PostMapping("/{projectId}/{repoName}/api/charts")
    fun upload(@ArtifactPathVariable artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap)
}
