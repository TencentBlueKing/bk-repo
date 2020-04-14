package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.stereotype.Component

/**
 *
 * @author: owenlxu
 * @date: 2020/04/20
 */
@Component
class DockerArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.DOCKER
}
