/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.repository.core

import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.artifact.constant.PARAM_DOWNLOAD
import com.tencent.bkrepo.common.artifact.event.ArtifactDownloadedEvent
import com.tencent.bkrepo.common.artifact.event.ArtifactResponseEvent
import com.tencent.bkrepo.common.artifact.event.ArtifactUploadedEvent
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.packages.VersionDownloadEvent
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactResponseException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.migration.MigrateDetail
import com.tencent.bkrepo.common.artifact.repository.redirect.DownloadRedirectManager
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResourceWriter
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.service.node.NodeSearchService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageDownloadsService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.Locale

/**
 * 构件仓库抽象类
 */
// TooGenericExceptionCaught: 需要捕捉文件传输阶段网络、IO等无法预知的异常
// LateinitUsage: AbstractArtifactRepository有大量子类，使用构造器注入将造成不便
@Suppress("TooGenericExceptionCaught", "LateinitUsage")
abstract class AbstractArtifactRepository : ArtifactRepository {

    @Autowired
    lateinit var nodeService: NodeService

    @Autowired
    lateinit var nodeSearchService: NodeSearchService

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var packageService: PackageService

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var storageManager: StorageManager

    @Autowired
    lateinit var artifactMetrics: ArtifactMetrics

    @Autowired
    lateinit var publisher: ApplicationEventPublisher

    @Autowired
    lateinit var packageDownloadsService: PackageDownloadsService

    @Autowired
    lateinit var artifactResourceWriter: ArtifactResourceWriter

    @Autowired
    private lateinit var taskAsyncExecutor: ThreadPoolTaskExecutor

    @Autowired
    lateinit var messageSupplier: MessageSupplier

    @Autowired
    lateinit var redirectManager: DownloadRedirectManager

    override fun upload(context: ArtifactUploadContext) {
        try {
            this.onUploadBefore(context)
            this.onUpload(context)
            this.onUploadSuccess(context)
        } catch (exception: RuntimeException) {
            this.onUploadFailed(context, exception)
        } finally {
            this.onUploadFinished(context)
        }
    }

    override fun download(context: ArtifactDownloadContext) {
        try {
            this.onDownloadBefore(context)
            if (this.onDownloadRedirect(context)) {
                return
            }
            val artifactResponse = this.onDownload(context)
                ?: throw ArtifactNotFoundException(context.artifactInfo.toString())
            val throughput = artifactResourceWriter.write(artifactResponse)
            if (artifactResponse.node != null) {
                ActionAuditContext.current().setInstance(artifactResponse.node)
            } else {
                ActionAuditContext.current().setInstance(artifactResponse.nodes)
            }
            this.onDownloadSuccess(context, artifactResponse, throughput)
        } catch (exception: ArtifactResponseException) {
            val principal = SecurityUtils.getPrincipal()
            val artifactInfo = context.artifactInfo
            val message = LocaleMessageUtils.getLocalizedMessage(exception.messageCode, exception.params)
            val code = exception.messageCode.getCode()
            val clientAddress = HttpContextHolder.getClientAddress()
            val xForwardedFor = HttpContextHolder.getXForwardedFor()
            val range = HeaderUtils.getHeader(HttpHeaders.RANGE) ?: StringPool.DASH
            logger.warn(
                "User[$principal],ip[$clientAddress] download artifact[$artifactInfo] failed[$code]$message" +
                    " X_FORWARDED_FOR: $xForwardedFor, range: $range", exception
            )
            ArtifactMetrics.getDownloadFailedCounter().increment()
        } catch (exception: ArtifactNotFoundException){
            throw exception
        } catch (exception: Exception) {
            this.onDownloadFailed(context, exception)
        } finally {
            this.onDownloadFinished(context)
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        throw MethodNotAllowedException()
    }

    override fun query(context: ArtifactQueryContext): Any? {
        throw MethodNotAllowedException()
    }

    override fun search(context: ArtifactSearchContext): List<Any> {
        throw MethodNotAllowedException()
    }

    override fun migrate(context: ArtifactMigrateContext): MigrateDetail {
        throw MethodNotAllowedException()
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
        throw MethodNotAllowedException()
    }

    /**
     * 上传成功回调
     */
    open fun onUploadSuccess(context: ArtifactUploadContext) {
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
     * 下载前回调
     */
    open fun onDownloadBefore(context: ArtifactDownloadContext) {
        // 控制浏览器直接下载，或打开预览
        context.useDisposition = context.request.getParameter(PARAM_DOWNLOAD)?.toBoolean() ?: false
        artifactMetrics.downloadingCount.incrementAndGet()
    }

    /**
     * 下载构件
     */
    open fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        throw MethodNotAllowedException()
    }

    /**
     * 下载成功回调
     */
    open fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        if (artifactResource.channel == ArtifactChannel.LOCAL) {
            buildDownloadRecord(context, artifactResource)?.let {
                taskAsyncExecutor.execute { packageDownloadsService.record(it) }
                publishPackageDownloadEvent(context, it)
            }
        }
        if (throughput != Throughput.EMPTY) {
            publisher.publishEvent(ArtifactResponseEvent(artifactResource, throughput, context.storageCredentials))
            publishNodeDownloadEvent(context)
            val range = HeaderUtils.getHeader(HttpHeaders.RANGE) ?: StringPool.DASH
            logger.info("User[${SecurityUtils.getPrincipal()}] download artifact[${context.artifactInfo}] success," +
                " range: $range")
        }
    }

    private fun publishNodeDownloadEvent(context: ArtifactDownloadContext) {
        publisher.publishEvent(ArtifactDownloadedEvent(context))
        if (context.artifacts.isNullOrEmpty()) {
            val event = ArtifactEvent(
                type = EventType.NODE_DOWNLOADED,
                projectId = context.projectId,
                repoName = context.repoName,
                resourceKey = context.artifactInfo.getArtifactFullPath(),
                userId = context.userId
            )
            messageSupplier.delegateToSupplier(data = event, topic = BINDING_OUT_NAME)
        } else {
            context.artifacts.forEach {
                val event = ArtifactEvent(
                    type = EventType.NODE_DOWNLOADED,
                    projectId = context.projectId,
                    repoName = context.repoName,
                    resourceKey = it.getArtifactFullPath(),
                    userId = context.userId
                )
                messageSupplier.delegateToSupplier(data = event, topic = BINDING_OUT_NAME)
            }
        }
    }

    /**
     * 构造下载记录
     *
     * 各依赖源自行判断是否需要增加下载记录，如果返回空则不记录
     */
    open fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        return null
    }

    /**
     * 下载失败回调
     *
     * 默认向上抛异常，由全局异常处理器处理
     */
    open fun onDownloadFailed(context: ArtifactDownloadContext, exception: Exception) {
        ArtifactMetrics.getDownloadFailedCounter().increment()
        throw exception
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

    /**
     * 是否支持重定向下载请求
     *
     * @param context 下载请求上下文
     *
     * @return true 支持重定向 false 不支持重定向
     */
    open fun supportRedirect(context: ArtifactDownloadContext): Boolean = false

    /**
     * 重定向下载请求
     *
     * @param context 下载请求上下文
     *
     * @return true 重定向成功 false 重定向失败
     */
    open fun onDownloadRedirect(context: ArtifactDownloadContext): Boolean {
        return false
    }

    private fun publishPackageDownloadEvent(context: ArtifactDownloadContext, record: PackageDownloadRecord) {
        if (context.repositoryDetail.type != RepositoryType.GENERIC) {
            val packageType = context.repositoryDetail.type.name
            val packageName = PackageKeys.resolveName(packageType.lowercase(Locale.getDefault()), record.packageKey)
            publisher.publishEvent(
                VersionDownloadEvent(
                    projectId = record.projectId,
                    repoName = record.repoName,
                    userId = SecurityUtils.getUserId(),
                    packageKey = record.packageKey,
                    packageVersion = record.packageVersion,
                    packageName = packageName,
                    packageType = packageType,
                    realIpAddress = HttpContextHolder.getClientAddress()
                )
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractArtifactRepository::class.java)
        private const val BINDING_OUT_NAME = "artifactEvent-out-0"
    }
}
