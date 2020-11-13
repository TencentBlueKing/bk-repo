package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.chart.ChartDeleteRequest
import com.tencent.bkrepo.helm.pojo.chart.ChartVersionDeleteRequest

interface ChartManipulationService {

    /**
     * 上传helm包
     */
    fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap)

    /**
     * 上传prov文件
     */
    fun uploadProv(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap)

    /**
     * 删除chart版本
     */
    fun deleteVersion(chartVersionDeleteRequest: ChartVersionDeleteRequest)

    /**
     * 删除chart
     */
    fun deletePackage(chartDeleteRequest: ChartDeleteRequest)
}
