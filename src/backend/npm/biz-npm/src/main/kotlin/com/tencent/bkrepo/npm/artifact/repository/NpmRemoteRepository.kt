/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.npm.artifact.repository

import com.tencent.bkrepo.common.api.constant.MediaTypes.APPLICATION_JSON_WITHOUT_CHARSET
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactMigrateContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.migration.MigrateDetail
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.REQUEST_URI
import com.tencent.bkrepo.npm.exception.NpmBadRequestException
import com.tencent.bkrepo.npm.handler.NpmPackageHandler
import com.tencent.bkrepo.npm.model.metadata.NpmVersionMetadata
import com.tencent.bkrepo.npm.pojo.NpmSearchInfoMap
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.utils.NpmUtils
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class NpmRemoteRepository(
    private val executor: ThreadPoolTaskExecutor,
    private val npmPackageHandler: NpmPackageHandler
) : RemoteRepository() {

    override fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        // 存储package-version.json文件
        executor.execute {
            getPackageVersionMetadata(context)?.run {
                npmPackageHandler.createVersion(
                    context.userId, context.artifactInfo, this, artifactResource.getTotalSize()
                )
            }
            super.onDownloadSuccess(context, artifactResource, throughput)
        }
    }

    private fun getPackageVersionMetadata(context: ArtifactDownloadContext): NpmVersionMetadata? {
        with(context) {
            val packageInfo = NpmUtils.parseNameAndVersionFromFullPath(artifactInfo.getArtifactFullPath())
            val versionMetadataFullPath = NpmUtils.getVersionPackageMetadataPath(packageInfo.first, packageInfo.second)
            val cacheNode = nodeClient.getNodeDetail(projectId, repoName, versionMetadataFullPath).data
            if (cacheNode != null) {
                logger.info(
                    "version metadata [$versionMetadataFullPath] is already exist in repo [$projectId/$repoName]"
                )
                storageManager.loadArtifactInputStream(cacheNode, context.storageCredentials)?.use {
                    return JsonUtils.objectMapper.readValue(it, NpmVersionMetadata::class.java)
                }
            }
            val remoteConfiguration = context.getRemoteConfiguration()
            val httpClient = createHttpClient(remoteConfiguration)
            context.putAttribute(REQUEST_URI, "/${packageInfo.first}/${packageInfo.second}")
            val downloadUri = createRemoteDownloadUrl(context)
            val request = Request.Builder().url(downloadUri).build()
            val response = httpClient.newCall(request).execute()
            logger.info("$response")
            return if (checkResponse(response)) {
                val artifactFile = createTempFile(response.body!!)
                context.putAttribute(NPM_FILE_FULL_PATH, versionMetadataFullPath)
                cacheArtifactFile(context, artifactFile)
                logger.info("cache version metadata [$versionMetadataFullPath] success.")
                artifactFile.getInputStream().use {
                    JsonUtils.objectMapper.readValue(it, NpmVersionMetadata::class.java)
                }
            } else null
        }
    }

    override fun upload(context: ArtifactUploadContext) {
        with(context) {
            val message = "Unable to upload npm package into a remote repository [$projectId/$repoName]"
            logger.warn(message)
            throw NpmBadRequestException(message)
        }
    }

    override fun query(context: ArtifactQueryContext): InputStream? {
        return (super.query(context) as InputStream?) ?: with(context) {
            val cacheNode = nodeClient.getNodeDetail(projectId, repoName, getStringAttribute(NPM_FILE_FULL_PATH)!!).data
            storageManager.loadArtifactInputStream(cacheNode, storageCredentials)
        }
    }

    override fun checkQueryResponse(response: Response): Boolean {
        return super.checkQueryResponse(response) && run {
            val contentType = response.body!!.contentType()
            if (!contentType.toString().contains(APPLICATION_JSON_WITHOUT_CHARSET)) {
                logger.warn("Response from [${response.request.url}] is not JSON format. Content-Type: $contentType")
                false
            } else true
        }
    }

    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val configuration = context.getRemoteConfiguration()
        val requestURI = context.getStringAttribute(REQUEST_URI)
        val artifactUri = requestURI ?: context.artifactInfo.getArtifactName()
        val queryString = context.request.queryString
        return UrlFormatter.format(configuration.url, artifactUri, queryString)
    }

    override fun onQueryResponse(context: ArtifactQueryContext, response: Response): InputStream? {
        val fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!
        val body = response.body!!
        val artifactFile = createTempFile(body)
        val sha256 = artifactFile.getFileSha256()
        with(context) {
            nodeClient.getNodeDetail(projectId, repoName, fullPath).data?.let {
                if (it.sha256.equals(sha256)) {
                    logger.info("artifact [$fullPath] is hit the cache.")
                    return artifactFile.getInputStream()
                }
                cacheArtifactFile(context, artifactFile)
            } ?: run {
                // 存储构件
                cacheArtifactFile(context, artifactFile)
            }
        }
        return artifactFile.getInputStream()
    }

    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = context.repositoryDetail.projectId,
            repoName = context.repositoryDetail.name,
            folder = false,
            fullPath = context.getStringAttribute(NPM_FILE_FULL_PATH)!!,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            overwrite = true,
            operator = context.userId
        )
    }

    override fun onSearchResponse(context: ArtifactSearchContext, response: Response): List<NpmSearchInfoMap> {
        val npmSearchResponse =
            JsonUtils.objectMapper.readValue(response.body!!.byteStream(), NpmSearchResponse::class.java)
        return npmSearchResponse.objects
    }

    override fun migrate(context: ArtifactMigrateContext): MigrateDetail {
        with(context) {
            val message = "Unable to migrate npm package info a remote repository [$projectId/$repoName]"
            logger.warn(message)
            throw NpmBadRequestException(message)
        }
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val packageInfo = NpmUtils.parseNameAndVersionFromFullPath(artifactInfo.getArtifactFullPath())
            with(packageInfo) {
                return PackageDownloadRecord(projectId, repoName, PackageKeys.ofNpm(first), second)
            }
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = context.getAttribute<List<*>>(NPM_FILE_FULL_PATH)
        val userId = context.userId
        fullPath?.forEach {
            nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, it.toString(), userId))
            logger.info("delete artifact $it success in repo [${context.artifactInfo.getRepoIdentify()}].")
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmRemoteRepository::class.java)
    }
}
