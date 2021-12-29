package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.pojo.metadata.HelmChartMetadata
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest

object ObjectBuilderUtil {

    fun buildPackageUpdateRequest(
        artifactInfo: ArtifactInfo,
        chartInfo: HelmChartMetadata
    ): PackageUpdateRequest {
        return buildPackageUpdateRequest(
            artifactInfo,
            chartInfo.name,
            chartInfo.appVersion.toString(),
            chartInfo.description
        )
    }

    fun buildPackageUpdateRequest(
        artifactInfo: ArtifactInfo,
        name: String,
        appVersion: String,
        description: String?
    ): PackageUpdateRequest {
        return PackageUpdateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            name = name,
            packageKey = PackageKeys.ofHelm(name),
            description = description,
            versionTag = null,
            extension = mapOf("appVersion" to appVersion)
        )
    }

    fun buildPackageVersionCreateRequest(
        userId: String,
        artifactInfo: ArtifactInfo,
        chartInfo: HelmChartMetadata,
        size: Long,
        isOverwrite: Boolean = false
    ): PackageVersionCreateRequest {
        return PackageVersionCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            packageName = chartInfo.name,
            packageKey = PackageKeys.ofHelm(chartInfo.name),
            packageType = PackageType.HELM,
            packageDescription = chartInfo.description,
            versionName = chartInfo.version,
            size = size,
            manifestPath = null,
            artifactPath = HelmUtils.getChartFileFullPath(chartInfo.name, chartInfo.version),
            stageTag = null,
            metadata = HelmMetadataUtils.convertToMap(chartInfo),
            overwrite = isOverwrite,
            createdBy = userId
        )
    }
}
