package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import io.swagger.annotations.Api
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute

@Api("chart 相关操作API")
interface ChartManipulationResource {
    @PostMapping("/{projectId}/{repoName}/api/charts")
    fun upload(@RequestAttribute userId: String, @ArtifactPathVariable artifactInfo: HelmArtifactInfo, file: ArtifactFile) {
    }
}
