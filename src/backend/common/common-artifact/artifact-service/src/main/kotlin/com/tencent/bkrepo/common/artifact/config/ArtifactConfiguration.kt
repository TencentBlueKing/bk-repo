package com.tencent.bkrepo.common.artifact.config

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
interface ArtifactConfiguration {
    fun getClientAuthConfig(): ClientAuthConfig = ClientAuthConfig()
    fun getRepositoryType(): RepositoryType? = null
}

data class ClientAuthConfig(
    val includePatterns: List<String> = listOf("/**"),
    val excludePatterns: List<String> = emptyList()
)
