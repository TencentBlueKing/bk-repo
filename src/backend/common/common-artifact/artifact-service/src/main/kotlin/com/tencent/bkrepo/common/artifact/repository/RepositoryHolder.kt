package com.tencent.bkrepo.common.artifact.repository

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.repository.constant.enums.RepositoryCategory

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
