package com.tencent.bkrepo.nuget.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
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

    override fun registrationIndex(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        return localRepository.registrationIndex(artifactInfo, registrationPath, isSemver2Endpoint)
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
