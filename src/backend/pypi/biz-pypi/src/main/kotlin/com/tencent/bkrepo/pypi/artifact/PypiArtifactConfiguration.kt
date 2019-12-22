package com.tencent.bkrepo.pypi.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.PYPI
}
