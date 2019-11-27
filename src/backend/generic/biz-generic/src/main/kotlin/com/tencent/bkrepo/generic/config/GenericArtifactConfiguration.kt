package com.tencent.bkrepo.generic.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.RepositoryType
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
@Component
class GenericArtifactConfiguration: ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.GENERIC
}