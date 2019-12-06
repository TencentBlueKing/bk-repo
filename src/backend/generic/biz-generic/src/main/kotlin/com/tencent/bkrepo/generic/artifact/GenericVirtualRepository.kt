package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import org.springframework.stereotype.Component
import java.io.File

/**
 *
 * @author: carrypan
 * @date: 2019/11/28
 */
@Component
class GenericVirtualRepository : VirtualRepository() {
    override fun onUpload(context: ArtifactUploadContext) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
