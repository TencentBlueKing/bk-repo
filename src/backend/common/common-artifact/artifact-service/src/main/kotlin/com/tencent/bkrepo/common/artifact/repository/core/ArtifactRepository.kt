package com.tencent.bkrepo.common.artifact.repository.core

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
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

    /**
     * 构件搜索
     */
    fun search(context: ArtifactSearchContext): JsonObject?

    /**
     * 构件列表
     */
    fun list(context: ArtifactListContext)

    /**
     * 移除构件
     */
    fun remove(context: ArtifactRemoveContext)
}
