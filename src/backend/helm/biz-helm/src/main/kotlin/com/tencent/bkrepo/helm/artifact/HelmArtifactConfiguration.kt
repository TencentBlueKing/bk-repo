package com.tencent.bkrepo.helm.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.ClientAuthConfig
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo.Companion.HELM_INSTALL_URL
import org.springframework.stereotype.Component

@Component
class HelmArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.HELM
    override fun getClientAuthConfig(): ClientAuthConfig = ClientAuthConfig(
        includePatterns = listOf("/**"),
        excludePatterns = listOf(HELM_INSTALL_URL)
    )
}
