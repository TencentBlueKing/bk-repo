package com.tencent.bkrepo.common.artifact.repository

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
interface LocalRepository {
    fun upload(context: ArtifactTransferContext)
    fun download(context: ArtifactTransferContext)
}