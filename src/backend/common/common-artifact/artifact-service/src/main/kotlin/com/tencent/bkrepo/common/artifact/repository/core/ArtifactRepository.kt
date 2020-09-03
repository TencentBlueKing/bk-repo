package com.tencent.bkrepo.common.artifact.repository.core

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.migration.MigrateDetail

/**
 * 构件仓库接口
 */
interface ArtifactRepository {
    /**
     * 上传构件
     *
     * @param context 构件上传上下文
     */
    fun upload(context: ArtifactUploadContext)

    /**
     * 下载构件
     *
     * @param context 构件下载上下文
     */
    fun download(context: ArtifactDownloadContext)

    /**
     * 移除构件
     *
     * @param context 构件移除上下文
     */
    fun remove(context: ArtifactRemoveContext)

    /**
     * 查询构件
     *
     * @param context 构件查询上下文
     */
    fun query(context: ArtifactQueryContext): Any?

    /**
     * 构件搜索
     *
     * @param context 构件搜索上下文
     */
    fun search(context: ArtifactSearchContext): List<Any>

    /**
     * 仓库迁移
     *
     * @param context 构件迁移上下文
     */
    fun migrate(context: ArtifactMigrateContext): MigrateDetail
}
