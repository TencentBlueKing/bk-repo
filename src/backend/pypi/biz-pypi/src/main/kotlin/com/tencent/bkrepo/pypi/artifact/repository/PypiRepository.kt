package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext

/**
 * pypi单独的接口
 */
interface PypiRepository {
    /**
     * pypi仓库下载依赖该接口返回的文件列表的地址链接。
     */
    fun htmlList(context: ArtifactListContext)
}
