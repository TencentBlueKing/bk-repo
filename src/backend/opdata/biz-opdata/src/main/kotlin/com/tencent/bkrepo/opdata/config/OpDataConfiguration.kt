package com.tencent.bkrepo.opdata.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import org.springframework.stereotype.Component

/**
 *
 * @author: owenlxu
 * @date: 2020/01/03
 */
@Component
class OpDataConfiguration : ArtifactConfiguration {
    override fun getRepositoryType() = RepositoryType.OPDATA
}
