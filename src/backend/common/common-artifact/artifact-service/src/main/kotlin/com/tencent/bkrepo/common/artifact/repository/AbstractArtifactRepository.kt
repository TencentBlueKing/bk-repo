package com.tencent.bkrepo.common.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory

/**
 * 构件仓库抽象类
 *
 * @author: carrypan
 * @date: 2019/11/27
 */
interface AbstractArtifactRepository : ArtifactRepository {

    override fun upload(context: ArtifactUploadContext) {
        val artifactUri = context.artifactInfo.getFullUri()
        val userId = context.userId

        try {
            this.onUploadValidate(context)
            logger.debug("User[$userId] validate upload artifact[$artifactUri] success.")
            this.onBeforeUpload(context)
            this.onUpload(context)
            logger.info("User[$userId] upload artifact[$artifactUri] success.")
            this.onUploadSuccess(context)
        } catch (validateException: ArtifactValidateException) {
            logger.warn("User[$userId] validate upload artifact[$artifactUri] failed: [$validateException]")
            this.onValidateFailed(context, validateException)
        } catch (exception: RuntimeException) {
            logger.warn("User[$userId] upload artifact[$artifactUri] failed: [$exception]")
            this.onUploadFailed(context, exception)
        }
    }

    override fun download(context: ArtifactDownloadContext) {
        val artifactUri = context.artifactInfo.getFullUri()
        val userId = context.userId

        try {
            this.onDownloadValidate(context)
            logger.debug("User[$userId] validate download artifact[$artifactUri] success")
            this.onBeforeDownload(context)
            this.onDownload(context)
            logger.info("User[$userId] download artifact[$artifactUri] success")
            this.onDownloadSuccess(context)
        } catch (validateException: ArtifactValidateException) {
            logger.warn("User[$userId] validate download artifact[$artifactUri] failed: [$validateException]")
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            logger.warn("User[$userId] download artifact[$artifactUri] failed: [$exception]")
            this.onDownloadFailed(context, exception)
        }
    }

    /**
     * 验证构件
     */
    @Throws(ArtifactValidateException::class)
    fun onUploadValidate(context: ArtifactUploadContext) {
        // 计算sha256
        val sha256 = FileDigestUtils.fileSha256(listOf(context.artifactFile.getInputStream()))
        context.contextAttributes[ATTRIBUTE_SHA256] = sha256
        // 校验size
        context.request.contentLengthLong.takeIf { it > 0 } ?: throw ArtifactValidateException("Content-Length must greater than 0.")
        context.artifactFile.getSize().takeIf { it > 0 } ?: throw ArtifactValidateException("File content can not be empty.")
    }

    /**
     * 上传前回调
     */
    fun onBeforeUpload(context: ArtifactUploadContext) {}

    /**
     * 获取节点创建请求
     */
    fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val artifactFile = context.artifactFile
        val sha256 = context.contextAttributes[ATTRIBUTE_SHA256] as String

        return NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = artifactInfo.artifactUri,
            size = artifactFile.getSize(),
            sha256 = sha256,
            operator = context.userId
        )
    }

    /**
     * 上传构件
     */
    fun onUpload(context: ArtifactUploadContext)

    /**
     * 上传成功回调
     */
    fun onUploadSuccess(context: ArtifactUploadContext) {}

    /**
     * 上传失败回调
     */
    fun onUploadFailed(context: ArtifactUploadContext, exception: Exception) {}

    /**
     * 下载验证
     */
    @Throws(ArtifactValidateException::class)
    fun onDownloadValidate(context: ArtifactDownloadContext) {}

    /**
     * 下载前回调
     */
    fun onBeforeDownload(context: ArtifactDownloadContext) {}

    /**
     * 获取节点fullPath
     */
    fun getNodeFullPath(context: ArtifactDownloadContext): String {
        return context.artifactInfo.artifactUri
    }

    /**
     * 下载构件
     */
    fun onDownload(context: ArtifactDownloadContext)

    /**
     * 下载成功回调
     */
    fun onDownloadSuccess(context: ArtifactDownloadContext) {}

    /**
     * 下载失败回调
     */
    fun onDownloadFailed(context: ArtifactDownloadContext, exception: Exception) {
        // 默认向上抛异常，由全局异常处理器处理
        throw exception
    }

    /**
     * 验证失败回调
     */
    fun onValidateFailed(context: ArtifactTransferContext, validateException: ArtifactValidateException) {}

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractArtifactRepository::class.java)
    }
}
