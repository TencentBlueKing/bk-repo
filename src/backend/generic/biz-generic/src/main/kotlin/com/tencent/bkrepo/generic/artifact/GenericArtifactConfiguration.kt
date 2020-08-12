package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.context.annotation.Configuration

@Configuration
class GenericArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.GENERIC
}
