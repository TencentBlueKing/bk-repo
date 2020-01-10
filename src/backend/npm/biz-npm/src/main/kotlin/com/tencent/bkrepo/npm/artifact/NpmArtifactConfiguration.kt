package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.ClientAuthConfig
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_ADD_USER_MAPPING_URI
import org.springframework.stereotype.Component

@Component
class NpmArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.NPM
    override fun getClientAuthConfig(): ClientAuthConfig = ClientAuthConfig(
        includePatterns = listOf("/**"),
        excludePatterns = listOf(NPM_ADD_USER_MAPPING_URI)
    )
}
