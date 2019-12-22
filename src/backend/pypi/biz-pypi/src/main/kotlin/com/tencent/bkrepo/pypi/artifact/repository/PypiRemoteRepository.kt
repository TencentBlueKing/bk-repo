package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.pojo.configuration.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import org.springframework.stereotype.Component
import java.lang.UnsupportedOperationException

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiRemoteRepository : RemoteRepository() {
    override fun onUpload(context: ArtifactUploadContext) {
        throw UnsupportedOperationException()
    }

    override fun genericRemoteDownloadUrl(context: ArtifactDownloadContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.artifactInfo.artifactUri
        return remoteConfiguration.url.trimEnd('/') + "/packages"+artifactUri
    }
}
