package com.tencent.bkrepo.common.artifact.repository.core

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext

/**
 * 构件仓库接口
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
     * 移除构件
     */
    fun remove(context: ArtifactRemoveContext)

    /**
     * 构件搜索
     */
    fun search(context: ArtifactSearchContext): Any?

    /**
     * 构件列表
     */
    fun list(context: ArtifactListContext): Any?

    /**
     * 仓库数据迁移
     */
    fun migrate(context: ArtifactMigrateContext): Any?
}
