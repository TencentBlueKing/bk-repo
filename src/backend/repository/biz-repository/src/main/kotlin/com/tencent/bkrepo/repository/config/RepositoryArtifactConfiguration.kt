package com.tencent.bkrepo.repository.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.ClientAuthConfig
import com.tencent.bkrepo.common.artifact.config.RepositoryType
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/11/25
 */
@Component
class RepositoryArtifactConfiguration: ArtifactConfiguration {
    override fun getClientAuthConfig() = ClientAuthConfig(pathPatterns = listOf("/api/**"))
}
