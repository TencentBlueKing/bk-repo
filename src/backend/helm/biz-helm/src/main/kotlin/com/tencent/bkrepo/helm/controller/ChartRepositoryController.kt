package com.tencent.bkrepo.helm.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_INDEX_YAML_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_INSTALL_URL
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_PROV_INSTALL_URL
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class ChartRepositoryController(
    private val chartRepositoryService: ChartRepositoryService
) {
    /**
     * query index.yaml
     */
    @GetMapping(HELM_INDEX_YAML_URL)
    fun queryIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo){
        chartRepositoryService.queryIndexYaml(artifactInfo)
    }

    /**
     * retrieved when you run helm install chartmuseum/mychart
     */
    @GetMapping(HELM_INSTALL_URL)
    fun installTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo){
        chartRepositoryService.installTgz(artifactInfo)
    }

    /**
     * retrieved when you run helm install with the --verify flag
     */
    @GetMapping(HELM_PROV_INSTALL_URL)
    fun installProv(@ArtifactPathVariable artifactInfo: HelmArtifactInfo){
        chartRepositoryService.installProv(artifactInfo)
    }

    /**
     * regenerate index.yaml
     */
    @GetMapping("/{projectId}/{repoName}/regenerate")
    fun regenerateIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo): Response<Void>{
        chartRepositoryService.regenerateIndexYaml(artifactInfo)
        return ResponseBuilder.success()
    }

    /**
     * batch install chart
     */
    @GetMapping("/{projectId}/{repoName}/batch/charts")
    fun batchInstallTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo, @RequestParam startTime: LocalDateTime){
        chartRepositoryService.batchInstallTgz(artifactInfo, startTime)
    }
}