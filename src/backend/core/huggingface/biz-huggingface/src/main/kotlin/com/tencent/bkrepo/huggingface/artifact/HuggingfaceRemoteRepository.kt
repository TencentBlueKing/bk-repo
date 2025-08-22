/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.artifact

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.EmptyInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.huggingface.constants.COMMIT_ID_HEADER
import com.tencent.bkrepo.huggingface.constants.REPO_TYPE_DATASET
import com.tencent.bkrepo.huggingface.constants.REPO_TYPE_MODEL
import com.tencent.bkrepo.huggingface.constants.REVISION_KEY
import com.tencent.bkrepo.huggingface.constants.REVISION_MAIN
import com.tencent.bkrepo.huggingface.exception.RevisionNotFoundException
import com.tencent.bkrepo.huggingface.pojo.DatasetInfo
import com.tencent.bkrepo.huggingface.pojo.ModelInfo
import com.tencent.bkrepo.huggingface.service.HfCommonService
import com.tencent.bkrepo.huggingface.util.HfApi
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import okhttp3.Headers
import okhttp3.Response
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HuggingfaceRemoteRepository(
    private val hfCommonService: HfCommonService,
) : RemoteRepository() {

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
        super.onDownloadBefore(context)
        val artifactInfo = context.artifactInfo
        if (artifactInfo is HuggingfaceArtifactInfo) {
            if (artifactInfo.getRevision().isNullOrEmpty() || artifactInfo.getRevision() == REVISION_MAIN) {
                transferRevision(context)
            }
        }
    }

    private fun transferRevision(context: ArtifactDownloadContext) {
        val configuration = context.getRemoteConfiguration()
        val response = HfApi.head(
            endpoint = configuration.url,
            token = configuration.credentials.password.orEmpty(),
            artifactUri = context.artifactInfo.getArtifactFullPath()
        )
        val commitId = response.headers[COMMIT_ID_HEADER.lowercase()]
        commitId?.let {
            HttpContextHolder.getRequest().setAttribute(REVISION_KEY, it)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        // revision下的文件不会变化，优先获取缓存
        return getCacheInfo(context)?.let { loadArtifactResource(it.first, context) } ?: run {
            val configuration = context.getRemoteConfiguration()
            val artifactInfo = context.artifactInfo as HuggingfaceArtifactInfo
            val response = HfApi.download(
                endpoint = configuration.url,
                token = configuration.credentials.password.orEmpty(),
                artifactUri = context.artifactInfo.getArtifactFullPath(),
                type = artifactInfo.type,
            )
            return onDownloadResponse(context, response, true, false)
        }
    }

    override fun addHeadersOfNode(headers: Headers) {
        super.addHeadersOfNode(headers)
        val response = HttpContextHolder.getResponse()
        headers[COMMIT_ID_HEADER.lowercase()]?.let { response.setHeader(COMMIT_ID_HEADER, it) }
    }

    override fun query(context: ArtifactQueryContext): Any? {
        val artifactInfo = context.artifactInfo as HuggingfaceRevisionInfo
        val packageKey: String
        val version: String
        val info = when (artifactInfo.type) {
            REPO_TYPE_MODEL -> {
                val modelInfo = getRevisionInfo<ModelInfo>(context)
                    ?: throw RevisionNotFoundException(artifactInfo.getRepoId() + "/" + artifactInfo.getRevision())
                packageKey = PackageKeys.ofHuggingface(REPO_TYPE_MODEL, artifactInfo.getRepoId())
                version = modelInfo.sha
                modelInfo
            }
            REPO_TYPE_DATASET -> {
                val datasetInfo = getRevisionInfo<DatasetInfo>(context)
                    ?: throw RevisionNotFoundException(artifactInfo.getRepoId() + "/" + artifactInfo.getRevision())
                packageKey = PackageKeys.ofHuggingface(REPO_TYPE_DATASET, artifactInfo.getRepoId())
                version = datasetInfo.sha
                datasetInfo
            }
            else -> throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "type:${artifactInfo.type}")
        }

        packageService.findVersionByName(context.projectId, context.repoName, packageKey, version) ?: run {
            val packageVersionCreateRequest = PackageVersionCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                packageName = artifactInfo.getRepoId(),
                packageKey = packageKey,
                packageType = PackageType.HUGGINGFACE,
                packageDescription = null,
                versionName = version,
                size = 0,
                artifactPath = "${artifactInfo.getRepoId()}/resolve/$version/",
                createdBy = SecurityUtils.getUserId(),
                overwrite = true
            )
            packageService.createPackageVersion(packageVersionCreateRequest)
        }
        return info
    }

    override fun onEmptyResponse(
        response: Response,
        range: Range,
        context: ArtifactDownloadContext,
    ): ArtifactInputStream {
        if (
            response.header(HttpHeaders.CONTENT_RANGE) == null &&
            response.header(HttpHeaders.CONTENT_LENGTH)!!.toLong() == 0L
        ) {
            // 通过head请求检测到空文件时，客户端将直接生成空文件而不再通过网络请求获取，此时仓库也需要生成空文件的缓存节点
            val artifactFile = ArtifactFileFactory.build(EmptyInputStream.INSTANCE)
            cacheArtifactFile(context, artifactFile)
        }
        return super.onEmptyResponse(response, range, context)
    }

    override fun buildDownloadRecord(context: ArtifactDownloadContext, artifactResource: ArtifactResource) =
        hfCommonService.buildDownloadRecord(context)

    override fun isExpiredForNonPositiveValue() = true

    private inline fun <reified T> getRevisionInfo(context: ArtifactQueryContext): T? {
        // revision信息可能会更新，优先级：未过期缓存 > 网络请求 > 已过期缓存
        // 缓存不存在时以网络请求为最终结果
        val (cacheNode, isExpired) = getCacheInfo(context) ?: return requestRevisionInfo<T>(context)
        // 缓存过期时先尝试网络请求，失败时回落到已过期缓存
        if (isExpired) requestRevisionInfo<T>(context)?.let { return it }
        return loadArtifactResource(cacheNode, context)?.getSingleStream()?.use { it.readJsonString<T>() }
    }

    private inline fun <reified T> requestRevisionInfo(context: ArtifactQueryContext): T? {
        val configuration = context.getRemoteConfiguration()
        val artifactInfo = context.artifactInfo as HuggingfaceRevisionInfo
        var sha: String? = null
        val revisionInfo = try {
            when (T::class) {
                ModelInfo::class -> {
                    HfApi.modelInfo(
                        endpoint = configuration.url,
                        token = configuration.credentials.password.orEmpty(),
                        repoId = artifactInfo.getRepoId(),
                        revision = artifactInfo.getRevision(),
                    ).also { sha = it.sha }
                }
                DatasetInfo::class -> {
                    HfApi.datasetInfo(
                        endpoint = configuration.url,
                        token = configuration.credentials.password.orEmpty(),
                        repoId = artifactInfo.getRepoId(),
                        revision = artifactInfo.getRevision(),
                    ).also { sha = it.sha }
                }
                else -> {
                    logger.error("unknown type ${T::class.simpleName}")
                    null
                }
            } as T?
        } catch (e: Exception) {
            logger.warn("An error occurred while requesting revision info from network.", e)
            null
        }
        return revisionInfo?.also { cacheRevisionInfo(context, it.toJsonString(), sha) }
    }

    private fun cacheRevisionInfo(context: ArtifactQueryContext, revisionInfo: String, sha: String?) {
        val artifactInfo = context.artifactInfo as HuggingfaceRevisionInfo
        val revision = artifactInfo.getRevision()
        val artifactFile = ArtifactFileFactory.build(revisionInfo.byteInputStream())
        val cacheNode = cacheArtifactFile(context, artifactFile)
        if (revision == REVISION_MAIN && cacheNode != null) {
            // 额外新建摘要命名的缓存节点，以便通过revision请求时可以从缓存获取
            if (sha.isNullOrEmpty()) {
                logger.error("Invalid sha value [$sha]")
                return
            }
            val nodeCreateRequest = buildCacheNodeCreateRequest(context, artifactFile)
                .copy(fullPath = artifactInfo.getArtifactFullPath().removeSuffix(REVISION_MAIN) + sha)
            storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, context.storageCredentials)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HuggingfaceRemoteRepository::class.java)
    }
}
