package com.tencent.bkrepo.nuget.service

import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.model.v3.RegistrationIndex

interface NugetV3ClientService {
    /**
     * 获取index.json内容
     */
    fun getFeed(artifactInfo: NugetArtifactInfo): String

    /**
     * 根据RegistrationsBaseUrl获取registration的index metadata
     */
    fun registration(artifactInfo: NugetArtifactInfo, packageId: String, registrationPath: String, isSemver2Endpoint: Boolean): RegistrationIndex

    /**
     * 下载 [packageId].[packageVersion].nupkg 包
     */
    fun download(artifactInfo: NugetArtifactInfo, packageId: String, packageVersion: String)
}