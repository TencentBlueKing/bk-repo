package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory

/**
 *
 * @author: carrypan
 * @date: 2019/11/27
 */
object RepositoryHolder {
    fun getRepository(category: RepositoryCategory): ArtifactRepository {
        return when (category) {
            RepositoryCategory.LOCAL -> SpringContextUtils.getBean(LocalRepository::class.java)
            RepositoryCategory.REMOTE -> SpringContextUtils.getBean(RemoteRepository::class.java)
            RepositoryCategory.VIRTUAL -> SpringContextUtils.getBean(VirtualRepository::class.java)
        }
    }
}
