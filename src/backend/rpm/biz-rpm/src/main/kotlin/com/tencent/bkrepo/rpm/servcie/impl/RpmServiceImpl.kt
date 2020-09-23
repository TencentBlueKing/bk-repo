package com.tencent.bkrepo.rpm.servcie.impl

import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RpmServiceImpl {
    @Autowired
    lateinit var repositoryClient: RepositoryClient

    fun getConfiguration(rpmArtifactInfo: RpmArtifactInfo): RepositoryConfiguration? {
        return with(rpmArtifactInfo) {
            (repositoryClient.getRepoInfo(projectId, repoName).data ?: return null).configuration
        }
    }
}
