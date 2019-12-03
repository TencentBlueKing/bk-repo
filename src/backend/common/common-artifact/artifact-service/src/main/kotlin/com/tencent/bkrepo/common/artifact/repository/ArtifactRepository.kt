package com.tencent.bkrepo.common.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext

/**
 * 构件仓库接口
 *
 * @author: carrypan
 * @date: 2019/11/27
 */
interface ArtifactRepository {
    /**
     * 构件上传
     */
    fun upload(context: ArtifactUploadContext)

    /**
     * 构件下载
     */
    fun download(context: ArtifactDownloadContext)
}
