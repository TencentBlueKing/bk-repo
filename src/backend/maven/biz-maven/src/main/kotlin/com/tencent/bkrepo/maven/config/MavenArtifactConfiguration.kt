package com.tencent.bkrepo.maven.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.RepositoryType
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
@Component
class MavenArtifactConfiguration: ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.MAVEN
}