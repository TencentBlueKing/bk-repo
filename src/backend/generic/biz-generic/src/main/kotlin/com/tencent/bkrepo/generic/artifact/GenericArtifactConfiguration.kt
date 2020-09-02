package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.springframework.context.annotation.Configuration

@Configuration
class GenericArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.GENERIC

    override fun getLocalRepository(): LocalRepository {
        return SpringContextUtils.getBean()
    }

    override fun getRemoteRepository(): RemoteRepository {
        return SpringContextUtils.getBean()
    }

    override fun getVirtualRepository(): VirtualRepository {
        return SpringContextUtils.getBean()
    }

    override fun getCompositeRepository(): CompositeRepository {
        return SpringContextUtils.getBean()
    }
}
