package com.tencent.bkrepo.common.artifact.config

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
interface ArtifactConfiguration {
    fun getClientAuthConfig(): ClientAuthConfig = ClientAuthConfig()
    fun getRepositoryType(): RepositoryType = RepositoryType.DOCKER
}

data class ClientAuthConfig(
    val includePatterns: List<String> = listOf("/**"),
    val excludePatterns: List<String> = emptyList()
)
