package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.repository.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/11/28
 */
@Component
class GenericRemoteRepository : RemoteRepository() {
    override fun onUpload(context: ArtifactUploadContext) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun onDownload(context: ArtifactDownloadContext) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
