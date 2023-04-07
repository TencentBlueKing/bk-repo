package com.tencent.bkrepo.nuget.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.exception.NugetException
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
import com.tencent.bkrepo.nuget.util.NugetUtils
import com.tencent.bkrepo.nuget.util.RemoteRegistrationUtils
import com.tencent.bkrepo.repository.api.ProxyChannelClient
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class NugetCompositeRepository(
    private val localRepository: NugetLocalRepository,
    private val remoteRepository: NugetRemoteRepository,
    proxyChannelClient: ProxyChannelClient
) : CompositeRepository(localRepository, remoteRepository, proxyChannelClient), NugetRepository {

    override fun enumerateVersions(context: ArtifactQueryContext, packageId: String): List<String>? {
        val localQueryResult = localRepository.enumerateVersions(context, packageId) ?: emptyList()
        val remoteQueryResult = mapFirstProxyRepo(context) {
            require(it is ArtifactQueryContext)
            remoteRepository.enumerateVersions(it, packageId)
        } ?: emptyList()
        return localQueryResult.union(remoteQueryResult).sorted()
    }

    override fun feed(artifactInfo: NugetArtifactInfo): Feed {
        return localRepository.feed(artifactInfo)
    }

    override fun registrationIndex(context: ArtifactQueryContext): RegistrationIndex? {
        val nugetArtifactInfo = context.artifactInfo as NugetRegistrationArtifactInfo
        val registrationPath = context.getStringAttribute("registrationPath")!!
        val localResult = localRepository.registrationIndex(context)
        val remoteResult = mapFirstProxyRepo(context) {
            require(it is ArtifactQueryContext)
            remoteRepository.registrationIndex(it)
        }
        val v3BaseUrl = NugetUtils.getV3Url(nugetArtifactInfo)
        val v3RegistrationUrl = "$v3BaseUrl/$registrationPath"
        return if (localResult == null) {
            remoteResult
        } else if (remoteResult == null) {
            localResult
        } else {
            RemoteRegistrationUtils.combineRegistrationIndex(
                localResult, remoteResult, nugetArtifactInfo, v3RegistrationUrl
            )
        }
    }

    override fun registrationPage(context: ArtifactQueryContext): RegistrationPage? {
        val nugetArtifactInfo = context.artifactInfo as NugetRegistrationArtifactInfo
        val registrationPath = context.getStringAttribute("registrationPath")!!
        val localResult = localRepository.registrationPage(context)
        val remoteResult = mapFirstProxyRepo(context) {
            require(it is ArtifactQueryContext)
            remoteRepository.registrationPage(it)
        }
        val v3BaseUrl = NugetUtils.getV3Url(nugetArtifactInfo)
        val v3RegistrationUrl = "$v3BaseUrl/$registrationPath"
        return if (localResult == null) {
            remoteResult
        } else if (remoteResult == null) {
            localResult
        } else {
            RemoteRegistrationUtils.combineRegistrationPage(
                localResult, remoteResult, nugetArtifactInfo, v3RegistrationUrl
            )
        }
    }

    fun proxyRegistrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        proxyChannelName: String,
        url: String,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): RegistrationPage? {
        val context = ArtifactQueryContext()
        val proxySetting = getProxyChannelList(context).find { it.name == proxyChannelName }
            ?: throw NugetException(
                "Proxy channel [$proxyChannelName] not found in [${artifactInfo.getRepoIdentify()}]"
            )
        val remoteContext = getContextFromProxyChannel(context, proxySetting)
        require(remoteContext is ArtifactQueryContext)
        val remoteResult = remoteRepository.proxyRegistrationPage(remoteContext, url) ?: return null
        val v2BaseUrl = NugetUtils.getV2Url(artifactInfo)
        val v3BaseUrl = NugetUtils.getV3Url(artifactInfo)
        return RemoteRegistrationUtils.rewriteRegistrationPageUrls(
            remoteResult, artifactInfo, v2BaseUrl, v3BaseUrl, registrationPath
        )
    }

    override fun registrationLeaf(context: ArtifactQueryContext): RegistrationLeaf? {
        return localRepository.registrationLeaf(context)
    }
}
