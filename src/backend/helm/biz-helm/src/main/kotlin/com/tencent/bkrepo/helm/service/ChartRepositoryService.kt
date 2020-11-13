package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.model.metadata.HelmIndexYamlMetadata
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

interface ChartRepositoryService {
    /**
     * get index.yaml
     */
    fun queryIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * install chart
     */
    fun installTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * install prov
     */
    fun installProv(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * regenerate index.yaml
     */
    fun regenerateIndexYaml(@ArtifactPathVariable artifactInfo: HelmArtifactInfo)

    /**
     * batch install chart
     */
    fun batchInstallTgz(@ArtifactPathVariable artifactInfo: HelmArtifactInfo, @RequestParam startTime: LocalDateTime)

    /**
     * fresh index yaml file
     */
    fun freshIndexFile(artifactInfo: HelmArtifactInfo)

    /**
     * 构造indexYaml元数据，[result]为自定义查询出来的node节点, [isInit] 是否需要初始化indexYaml元数据
     */
    fun buildIndexYamlMetadata(
        result: List<Map<String, Any?>>,
        artifactInfo: HelmArtifactInfo,
        isInit: Boolean = false
    ): HelmIndexYamlMetadata
}
