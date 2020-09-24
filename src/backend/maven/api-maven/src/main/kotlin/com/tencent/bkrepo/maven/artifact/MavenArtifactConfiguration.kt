package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.context.annotation.Configuration

@Configuration
class MavenArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.MAVEN
}
