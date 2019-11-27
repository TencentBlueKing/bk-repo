package com.tencent.bkrepo.common.artifact.repository

import org.slf4j.LoggerFactory

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
interface LocalRepository: ArtifactRepository {
    fun upload(context: ArtifactTransferContext) {
        this.onUploadValidate(context)
        try {
            this.onUpload(context)
            this.onUploadSuccess(context)
        } catch (exception: Exception) {
            this.onUploadFailed(context, exception)
        }
    }

    fun download(context: ArtifactTransferContext) {
        this.onDownloadValidate(context)
        try {
            this.onDownload(context)
            this.onDownloadSuccess(context)
        } catch (exception: Exception) {
            this.onDownloadFailed(context, exception)
        }
    }

    fun onUploadValidate(context: ArtifactTransferContext) {
        val repo = context.repo
    }

    fun onUpload(context: ArtifactTransferContext)

    fun onUploadSuccess(context: ArtifactTransferContext) {}

    fun onUploadFailed(context: ArtifactTransferContext, exception: Exception) {}

    fun onDownloadValidate(context: ArtifactTransferContext) {
        val repo = context.repo
    }

    fun onDownload(context: ArtifactTransferContext)

    fun onDownloadSuccess(context: ArtifactTransferContext) {}

    fun onDownloadFailed(context: ArtifactTransferContext, exception: Exception) {}

    companion object {
        private val logger = LoggerFactory.getLogger(LocalRepository::class.java)
    }
}