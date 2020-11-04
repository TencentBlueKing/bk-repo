package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.user.PackageVersionInfo
import java.time.LocalDateTime

interface ChartInfoService {
    /**
     * 查看chart列表
     */
    fun allChartsList(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime? = null): Map<String, Any>

    /**
     * 查看chart是否存在
     */
    fun isExists(artifactInfo: HelmArtifactInfo)

    /**
     * 查询版本详情
     */
    fun detailVersion(userId: String, artifactInfo: HelmArtifactInfo, packageKey: String, version: String): PackageVersionInfo
}