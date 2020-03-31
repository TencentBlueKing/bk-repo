package com.tencent.bkrepo.common.artifact.repository.core

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.config.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.exception.UnsupportedMethodException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.util.HttpResponseUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 构件仓库抽象类
 *
 * @author: carrypan
 * @date: 2019/11/27
 */
interface AbstractArtifactRepository : ArtifactRepository {

    override fun upload(context: ArtifactUploadContext) {
        try {
            this.onUploadValidate(context)
            this.onBeforeUpload(context)
            this.onUpload(context)
            this.onUploadSuccess(context)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            this.onUploadFailed(context, exception)
        }
    }

    override fun download(context: ArtifactDownloadContext) {
        try {
            this.onDownloadValidate(context)
            this.onBeforeDownload(context)
            val file = this.onDownload(context) ?: throw ArtifactNotFoundException("Artifact[${context.artifactInfo.getFullUri()}] not found")
            val name = NodeUtils.getName(context.artifactInfo.artifactUri)
            HttpResponseUtils.response(name, file)
            this.onDownloadSuccess(context, file)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            this.onDownloadFailed(context, exception)
        }
    }

    override fun search(context: ArtifactSearchContext): Any? {
        throw UnsupportedMethodException()
    }

    override fun list(context: ArtifactListContext): Any? {
        throw UnsupportedMethodException()
    }

    override fun remove(context: ArtifactRemoveContext) {
        throw UnsupportedMethodException()
    }

    /**
     * 验证构件
     */
    @Throws(ArtifactValidateException::class)
    fun onUploadValidate(context: ArtifactUploadContext) {
        val sha256Map = mutableMapOf<String, String>()
        val md5Map = mutableMapOf<String, String>()
        // 计算sha256和md5
        context.artifactFileMap.entries.forEach { (name, file) ->
            sha256Map[name] = FileDigestUtils.fileSha256(file.getInputStream())
            md5Map[name] = FileDigestUtils.fileMd5(file.getInputStream())
            if (name == OCTET_STREAM) {
                context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] = sha256Map[name] as String
                context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] = md5Map[name] as String
            }
        }
        context.contextAttributes[ATTRIBUTE_SHA256MAP] = sha256Map
        context.contextAttributes[ATTRIBUTE_MD5MAP] = md5Map
    }

    /**
     * 上传前回调
     */
    fun onBeforeUpload(context: ArtifactUploadContext) {}

    /**
     * 上传构件
     */
    fun onUpload(context: ArtifactUploadContext) {
        throw UnsupportedMethodException()
    }

    /**
     * 上传成功回调
     */
    fun onUploadSuccess(context: ArtifactUploadContext) {
        val artifactUri = context.artifactInfo.getFullUri()
        val userId = context.userId
        logger.info("User[$userId] upload artifact[$artifactUri] success")
    }

    /**
     * 上传失败回调
     */
    fun onUploadFailed(context: ArtifactUploadContext, exception: Exception) {
        // 默认向上抛异常，由全局异常处理器处理
        throw exception
    }

    /**
     * 下载验证
     */
    @Throws(ArtifactValidateException::class)
    fun onDownloadValidate(context: ArtifactDownloadContext) {
    }

    /**
     * 下载前回调
     */
    fun onBeforeDownload(context: ArtifactDownloadContext) {}

    /**
     * 下载构件
     */
    fun onDownload(context: ArtifactDownloadContext): File? {
        throw UnsupportedMethodException()
    }

    /**
     * 下载成功回调
     */
    fun onDownloadSuccess(context: ArtifactDownloadContext, file: File) {
        val artifactUri = context.artifactInfo.getFullUri()
        val userId = context.userId
        logger.info("User[$userId] download artifact[$artifactUri] success")
    }

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
    fun onValidateFailed(context: ArtifactTransferContext, validateException: ArtifactValidateException) {
        // 默认向上抛异常，由全局异常处理器处理
        throw validateException
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractArtifactRepository::class.java)
    }
}
