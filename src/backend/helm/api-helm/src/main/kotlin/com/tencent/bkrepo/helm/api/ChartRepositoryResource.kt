package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping

@Api("helm仓库获取tgz包")
interface ChartRepositoryResource {
    @ApiOperation("get index.yaml")
    @GetMapping("/{projectId}/{repoName}/index.yaml")
    fun getIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    @ApiOperation("retrieved when you run helm install chartmuseum/mychart")
    @GetMapping("/{projectId}/{repoName}/charts/*.tgz")
    fun installTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)
}
