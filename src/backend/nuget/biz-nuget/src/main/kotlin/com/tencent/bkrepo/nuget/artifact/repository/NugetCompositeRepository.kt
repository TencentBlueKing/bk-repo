package com.tencent.bkrepo.nuget.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.constant.NUGET_V3_NOT_FOUND
import com.tencent.bkrepo.nuget.exception.NugetException
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.util.NugetUtils
import com.tencent.bkrepo.nuget.util.NugetV3RemoteRepositoryUtils
import com.tencent.bkrepo.repository.api.ProxyChannelClient
import org.springframework.context.annotation.Primary
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
@Primary
class NugetCompositeRepository(
    private val localRepository: NugetLocalRepository,
    private val remoteRepository: NugetRemoteRepository,
    proxyChannelClient: ProxyChannelClient
) : CompositeRepository(localRepository, remoteRepository, proxyChannelClient), NugetRepository {

    override fun query(context: ArtifactQueryContext): Any? {
        val localQueryResult = (localRepository.query(context) as? List<*>)?.map { it.toString() }
        val remoteQueryResult = mapFirstProxyRepo(context) {
            require(it is ArtifactQueryContext)
            (remoteRepository.query(it) as? List<*>)?.map { element -> element.toString() }
        } ?: return localQueryResult
        return if (localQueryResult.isNullOrEmpty()) remoteQueryResult else
            localQueryResult.minus(remoteQueryResult.toSet()).plus(remoteQueryResult).sorted()
    }

    override fun feed(artifactInfo: NugetArtifactInfo): ResponseEntity<Any> {
        return localRepository.feed(artifactInfo)
    }

    fun registrationIndex(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        val context = ArtifactQueryContext()
        val localResult = localRepository.registrationIndex(artifactInfo, registrationPath, isSemver2Endpoint)
        val remoteResult = mapFirstProxyRepo(context) {
            require(it is ArtifactQueryContext)
            remoteRepository.registrationIndex(artifactInfo, it)
        }
        val v2BaseUrl = NugetUtils.getV2Url(artifactInfo)
        val v3BaseUrl = NugetUtils.getV3Url(artifactInfo)
        val v3RegistrationUrl = "$v3BaseUrl/$registrationPath"
        val proxyChannelName = context.getStringAttribute("proxyChannelName")
        // 本地和远程均无查询结果
        return if (localResult == null && remoteResult == null) {
            ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                .body(NUGET_V3_NOT_FOUND)
        // 仅远程查询结果
        } else if (localResult == null && remoteResult != null) {
            val rewriteRemoteResult = NugetV3RemoteRepositoryUtils.rewriteRegistrationIndexUrls(
                remoteResult, artifactInfo, v2BaseUrl, v3BaseUrl, registrationPath, proxyChannelName
            )
            rewriteRemoteResult.items.forEach { it.items?.forEach { item -> item.sourceType = "PROXY" } }
            ResponseEntity.ok(rewriteRemoteResult)
        // 仅本地查询结果
        } else if (remoteResult == null) {
            ResponseEntity.ok(localResult)
        // 合并本地结果和远程结果
        } else {
            val rewriteRemoteResult = NugetV3RemoteRepositoryUtils.rewriteRegistrationIndexUrls(
                remoteResult, artifactInfo, v2BaseUrl, v3BaseUrl, registrationPath, proxyChannelName
            )
            val compositeRegistrationIndex = NugetV3RemoteRepositoryUtils.combineRegistrationIndex(
                localResult!!, rewriteRemoteResult, artifactInfo, v3RegistrationUrl
            )
            ResponseEntity.ok(compositeRegistrationIndex)
        }
    }

    fun proxyRegistrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        proxyChannelName: String,
        url: String,
        registrationPath: String
    ): ResponseEntity<Any> {
        val context = ArtifactQueryContext()
        val proxySetting = getProxyChannelList(context).find { it.name == proxyChannelName } ?:
            throw NugetException("Proxy channel [$proxyChannelName] not found in [${artifactInfo.getRepoIdentify()}]")
        val remoteContext = getContextFromProxyChannel(context, proxySetting)
        require(remoteContext is ArtifactQueryContext)
        val remoteResult = remoteRepository.proxyRegistrationPage(remoteContext, url)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                .body(NUGET_V3_NOT_FOUND)
        val v2BaseUrl = NugetUtils.getV2Url(artifactInfo)
        val v3BaseUrl = NugetUtils.getV3Url(artifactInfo)
        val rewriteRemoteResult = NugetV3RemoteRepositoryUtils.rewriteRegistrationPageUrls(
            remoteResult, artifactInfo, v2BaseUrl, v3BaseUrl, registrationPath
        )
        rewriteRemoteResult.items.forEach { it.sourceType = "PROXY" }
        return ResponseEntity.ok(rewriteRemoteResult)
    }

    override fun registrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        return localRepository.registrationPage(artifactInfo, registrationPath, isSemver2Endpoint)
    }

    override fun registrationLeaf(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        return localRepository.registrationLeaf(artifactInfo, registrationPath, isSemver2Endpoint)
    }
}
