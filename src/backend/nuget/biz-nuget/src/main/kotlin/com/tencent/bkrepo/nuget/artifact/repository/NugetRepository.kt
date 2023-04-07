package com.tencent.bkrepo.nuget.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage

interface NugetRepository : ArtifactRepository {

    /**
     * 根据RegistrationsBaseUrl获取registration index metadata
     */
    fun registrationIndex(context: ArtifactQueryContext): RegistrationIndex?

    /**
     * 根据RegistrationsBaseUrl获取registration page metadata
     */
    fun registrationPage(context: ArtifactQueryContext): RegistrationPage?

    /**
     * 根据RegistrationsBaseUrl获取registration leaf metadata
     */
    fun registrationLeaf(context: ArtifactQueryContext): RegistrationLeaf?

    /**
     * 查找服务的索引文件index.json
     */
    fun feed(artifactInfo: NugetArtifactInfo): Feed

    /**
     * 枚举包的所有版本
     */
    fun enumerateVersions(context: ArtifactQueryContext, packageId: String): List<String>?
}
