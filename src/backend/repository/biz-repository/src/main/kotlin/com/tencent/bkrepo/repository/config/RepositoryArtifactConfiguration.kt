package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.ClientAuthConfig
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
@Component
class RepositoryArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.NONE
    override fun getClientAuthConfig() = ClientAuthConfig(includePatterns = listOf("/api/**", "/list/**"))
}
