package com.tencent.bkrepo.common.artifact.config

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
interface ArtifactConfiguration {
    val clientAuthConfig: ClientAuthConfig
    val repositoryType: RepositoryType
}

open class DefaultArtifactConfiguration: ArtifactConfiguration {
    override val clientAuthConfig = ClientAuthConfig()
    override val repositoryType = RepositoryType.GENERIC
}

data class ClientAuthConfig(
    val pathPatterns: List<String> = listOf("/**"),
    val excludePatterns: List<String> = emptyList()
)
