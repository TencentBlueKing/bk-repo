package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_INDEX_YAML_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_INSTALL_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_PROV_INSTALL_URL
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

@Api("helm仓库获取tgz包")
interface ChartRepositoryResource {
    @ApiOperation("get index.yaml")
    @GetMapping(HELM_INDEX_YAML_URL)
    fun getIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    @ApiOperation("retrieved when you run helm install chartmuseum/mychart")
    @GetMapping(HELM_INSTALL_URL)
    fun installTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    @ApiOperation("retrieved when you run helm install with the --verify flag")
    @GetMapping(HELM_PROV_INSTALL_URL)
    fun installProv(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    @ApiOperation("regenerate index.yaml")
    @GetMapping("/{projectId}/{repoName}/regenerate")
    fun regenerateIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo): Response<Void>

    @ApiOperation("batch install chart")
    @GetMapping("/{projectId}/{repoName}/batch/charts")
    fun batchInstallTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo, @RequestParam startTime: LocalDateTime)
}
