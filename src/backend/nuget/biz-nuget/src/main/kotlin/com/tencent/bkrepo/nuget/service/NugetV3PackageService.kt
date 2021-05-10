package com.tencent.bkrepo.nuget.service

import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.pojo.response.VersionListResponse
import com.tencent.bkrepo.nuget.model.v3.RegistrationIndex
import com.tencent.bkrepo.nuget.model.v3.search.SearchRequest
import com.tencent.bkrepo.nuget.model.v3.search.SearchResponse
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo

interface NugetV3PackageService {

    /**
     * 根据RegistrationsBaseUrl获取registration的index metadata
     */
    fun registration(
        artifactInfo: NugetArtifactInfo,
        packageId: String,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): RegistrationIndex

    /**
     * 下载 .nupkg 包
     */
    fun downloadPackageContent(artifactInfo: NugetDownloadArtifactInfo)

    /**
     * 下载 .nuspec 包
     */
    fun downloadPackageManifest(artifactInfo: NugetDownloadArtifactInfo)

    /**
     * 查询包的所有版本
     */
    fun packageVersions(artifactInfo: NugetArtifactInfo, packageId: String): VersionListResponse

    /**
     * 根据[searchRequest]里面的条件进行搜索
     */
    fun search(artifactInfo: NugetArtifactInfo, searchRequest: SearchRequest): SearchResponse
}
