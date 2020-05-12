package com.tencent.bkrepo.composer.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.stereotype.Component

@Component
class ComposerArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.COMPOSER
}
