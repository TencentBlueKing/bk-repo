package com.tencent.bkrepo.common.artifact.repository

import com.tencent.bkrepo.repository.constant.enums.RepositoryCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/11/27
 */
@Component
class RepositoryHolder {

    @Autowired
    private lateinit var localRepository: LocalRepository

    @Autowired
    private lateinit var remoteRepository: RemoteRepository

    @Autowired
    private lateinit var virtualRepository: VirtualRepository

    fun getRepository(category: RepositoryCategory): ArtifactRepository {
        return when(category) {
            RepositoryCategory.LOCAL -> localRepository
            RepositoryCategory.REMOTE -> remoteRepository
            RepositoryCategory.VIRTUAL -> virtualRepository
        }
    }
}