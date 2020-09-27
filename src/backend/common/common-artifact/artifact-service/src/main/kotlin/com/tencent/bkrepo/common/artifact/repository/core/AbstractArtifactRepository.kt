package com.tencent.bkrepo.common.artifact.repository.core

import com.tencent.bkrepo.common.artifact.event.ArtifactUploadedEvent
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.exception.UnsupportedMethodException
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.migration.MigrateDetail
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.http.ArtifactResourceWriter
import com.tencent.bkrepo.common.security.http.SecurityUtils
import com.tencent.bkrepo.repository.api.PackageDownloadStatisticsClient
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.Executor
import javax.annotation.Resource

/**
 * 构件仓库抽象类
 */
@Suppress("TooGenericExceptionCaught")
abstract class AbstractArtifactRepository : ArtifactRepository {

    @Autowired
    lateinit var artifactMetrics: ArtifactMetrics

    @Autowired
    lateinit var publisher: ApplicationEventPublisher

    @Autowired
    lateinit var packageDownloadStatisticsClient: PackageDownloadStatisticsClient

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    override fun upload(context: ArtifactUploadContext) {
        try {
            this.onUploadBefore(context)
            this.onUploadValidate(context)
            this.onUpload(context)
            this.onUploadSuccess(context)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            this.onUploadFailed(context, exception)
        } finally {
            this.onUploadFinished(context)
        }
    }

    override fun download(context: ArtifactDownloadContext) {
        try {
            this.onDownloadBefore(context)
            this.onDownloadValidate(context)
            val artifactResponse = this.onDownload(context)
                ?: throw ArtifactNotFoundException("Artifact[${context.artifactInfo}] not found")
            ArtifactResourceWriter.write(artifactResponse)
            this.onDownloadSuccess(context, artifactResponse)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            this.onDownloadFailed(context, exception)
        } finally {
            this.onDownloadFinished(context)
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        throw UnsupportedMethodException()
    }

    override fun query(context: ArtifactQueryContext): Any? {
        throw UnsupportedMethodException()
    }

    override fun search(context: ArtifactSearchContext): List<Any> {
        throw UnsupportedMethodException()
    }

    override fun migrate(context: ArtifactMigrateContext): MigrateDetail {
        throw UnsupportedMethodException()
    }

    /**
     * 验证构件
     */
    @Throws(ArtifactValidateException::class)
    open fun onUploadValidate(context: ArtifactUploadContext) {
    }

    /**
     * 上传前回调
     */
    open fun onUploadBefore(context: ArtifactUploadContext) {
        artifactMetrics.uploadingCount.incrementAndGet()
    }

    /**
     * 上传构件
     */
    open fun onUpload(context: ArtifactUploadContext) {
        throw UnsupportedMethodException()
    }

    /**
     * 上传成功回调
     */
    open fun onUploadSuccess(context: ArtifactUploadContext) {
        artifactMetrics.uploadedCounter.increment()
        publisher.publishEvent(ArtifactUploadedEvent(context))
        val artifactInfo = context.artifactInfo
        logger.info("User[${SecurityUtils.getPrincipal()}] upload artifact[$artifactInfo] success")
    }

    /**
     * 上传失败回调
     */
    open fun onUploadFailed(context: ArtifactUploadContext, exception: Exception) {
        // 默认向上抛异常，由全局异常处理器处理
        throw exception
    }

    /**
     * 下载验证
     */
    @Throws(ArtifactValidateException::class)
    open fun onDownloadValidate(context: ArtifactDownloadContext) {
    }

    /**
     * 下载前回调
     */
    open fun onDownloadBefore(context: ArtifactDownloadContext) {
        artifactMetrics.downloadingCount.incrementAndGet()
    }

    /**
     * 下载构件
     */
    open fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        throw UnsupportedMethodException()
    }

    /**
     * 下载成功回调
     */
    open fun onDownloadSuccess(context: ArtifactDownloadContext, artifactResource: ArtifactResource) {
        artifactMetrics.downloadedCounter.increment()
        if (artifactResource.channel == ArtifactChannel.LOCAL) {
            buildDownloadRecord(context, artifactResource)?.let {
                taskAsyncExecutor.execute { packageDownloadStatisticsClient.add(it) }
            }
        }
        logger.info("User[${SecurityUtils.getPrincipal()}] download artifact[${context.artifactInfo}] success")
    }

    /**
     * 构造下载记录
     *
     * 各依赖源自行判断是否需要增加下载记录，如果返回空则不记录
     */
    open fun buildDownloadRecord(context: ArtifactDownloadContext, artifactResource: ArtifactResource): DownloadStatisticsAddRequest? {
        return null
    }

    /**
     * 下载失败回调
     *
     * 默认向上抛异常，由全局异常处理器处理
     */
    open fun onDownloadFailed(context: ArtifactDownloadContext, exception: Exception) {
        throw exception
    }

    /**
     * 验证失败回调
     *
     * 默认向上抛异常，由全局异常处理器处理
     */
    open fun onValidateFailed(context: ArtifactContext, validateException: ArtifactValidateException) {
        throw validateException
    }

    /**
     * 上传结束回调
     */
    open fun onUploadFinished(context: ArtifactUploadContext) {
        artifactMetrics.uploadingCount.decrementAndGet()
    }

    /**
     * 下载结束回调
     */
    open fun onDownloadFinished(context: ArtifactDownloadContext) {
        artifactMetrics.downloadingCount.decrementAndGet()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractArtifactRepository::class.java)
    }
}