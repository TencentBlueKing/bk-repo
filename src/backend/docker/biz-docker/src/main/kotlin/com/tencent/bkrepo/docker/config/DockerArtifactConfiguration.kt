package com.tencent.bkrepo.docker.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.ClientAuthConfig
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.stereotype.Component

/**
 * config the path to validate privilege
 * @author: owenlxu
 * @date: 2019/11/27
 */

@Component
class DockerArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.DOCKER

    override fun getClientAuthConfig() = ClientAuthConfig(
        includePatterns = listOf("/**"),
        excludePatterns = listOf("/v2/auth", "/v2/_catalog", "/v2/*/*/*/tags/list")
    )
}
