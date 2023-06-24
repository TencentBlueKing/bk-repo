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

package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpHeaders.USER_AGENT
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.pojo.RepositoryIdentify
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.pypi.FLUSH_CACHE_EXPIRE
import com.tencent.bkrepo.pypi.REMOTE_HTML_CACHE_FULL_PATH
import com.tencent.bkrepo.pypi.XML_RPC_URI
import com.tencent.bkrepo.pypi.artifact.xml.XmlConvertUtil
import com.tencent.bkrepo.pypi.constants.ARTIFACT_LIST
import com.tencent.bkrepo.pypi.constants.METADATA
import com.tencent.bkrepo.pypi.constants.NAME
import com.tencent.bkrepo.pypi.constants.PACKAGE_KEY
import com.tencent.bkrepo.pypi.constants.VERSION
import com.tencent.bkrepo.pypi.exception.PypiRemoteSearchException
import com.tencent.bkrepo.pypi.util.ArtifactFileUtils
import com.tencent.bkrepo.pypi.pojo.Basic
import com.tencent.bkrepo.pypi.pojo.PypiArtifactVersionData
import com.tencent.bkrepo.pypi.util.XmlUtils.readXml
import com.tencent.bkrepo.pypi.util.pojo.PypiInfo
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Component
class PypiRemoteRepository : RemoteRepository() {

    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val remoteConfiguration = context.getRemoteConfiguration()
        val artifactUri = context.artifactInfo.getArtifactFullPath()
        return remoteConfiguration.url.trimEnd('/').removeSuffix("/simple") + "/packages" + artifactUri
    }

    /**
     * 生成远程list url
     */
    fun generateRemoteListUrl(context: ArtifactQueryContext): String {
        val remoteConfiguration = context.getRemoteConfiguration()
        val artifactUri = context.artifactInfo.getArtifactFullPath()
        return remoteConfiguration.url.removeSuffix("/").removeSuffix("simple").removeSuffix("/") +
            "/simple$artifactUri"
    }

    override fun query(context: ArtifactQueryContext): Any? {
        return if (context.request.servletPath.startsWith("/ext/version/detail")) {
            getVersionDetail(context)
        } else if (context.artifactInfo.getArtifactFullPath() == "/") {
            getCacheHtml(context)
        } else {
            remoteRequest(context)
        }
    }

    fun remoteRequest(context: ArtifactQueryContext): String? {
        val listUri = generateRemoteListUrl(context)
        val remoteConfiguration = context.getRemoteConfiguration()
        val okHttpClient: OkHttpClient = createHttpClient(remoteConfiguration)
        val build: Request = Request.Builder()
            .get()
            .url(listUri)
            .removeHeader(USER_AGENT)
            .addHeader(USER_AGENT, "${UUID.randomUUID()}")
            .build()
        return okHttpClient.newCall(build).execute().body?.string()
    }

    /**
     * 获取项目-仓库缓存对应的html文件
     */
    fun getCacheHtml(context: ArtifactQueryContext): String? {
        val repositoryDetail = context.repositoryDetail
        val projectId = repositoryDetail.projectId
        val repoName = repositoryDetail.name
        val fullPath = REMOTE_HTML_CACHE_FULL_PATH
        var node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
        loop@for (i in 1..3) {
            cacheRemoteRepoList(context)
            node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
            if (node != null) break@loop
        }
        if (node == null) return "Can not cache remote html"
        node.takeIf { !it.folder } ?: return null
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        val date = LocalDateTime.parse(node.lastModifiedDate, format)
        val currentTime = LocalDateTime.now()
        val duration = Duration.between(date, currentTime).toMinutes()
        GlobalScope.launch {
            if (duration > FLUSH_CACHE_EXPIRE) {
                cacheRemoteRepoList(context)
            }
        }.start()
        val stringBuilder = StringBuilder()
        storageService.load(node.sha256!!, Range.full(node.size), context.storageCredentials)?.use {
            artifactInputStream ->
            var line: String?
            val br = BufferedReader(InputStreamReader(artifactInputStream))
            while (br.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
        }
        return stringBuilder.toString()
    }

    /**
     * 缓存html文件
     */
    fun cacheRemoteRepoList(context: ArtifactQueryContext) {
        val listUri = generateRemoteListUrl(context)
        val remoteConfiguration = context.getRemoteConfiguration()
        val okHttpClient: OkHttpClient = createHttpClient(remoteConfiguration)
        val build: Request = Request.Builder().get().url(listUri).build()
        val htmlContent = okHttpClient.newCall(build).execute().body?.string()
        htmlContent?.let { storeCacheHtml(context, it) }
    }

    fun storeCacheHtml(context: ArtifactQueryContext, htmlContent: String) {
        val artifactFile = ArtifactFileFactory.build(ByteArrayInputStream(htmlContent.toByteArray()))
        val nodeCreateRequest = getNodeCreateRequest(context, artifactFile)
        store(nodeCreateRequest, artifactFile, context.storageCredentials)
    }

    /**
     * 需要单独给fullpath赋值。
     */
    fun getNodeCreateRequest(context: ArtifactQueryContext, artifactFile: ArtifactFile): NodeCreateRequest {
        return super.buildCacheNodeCreateRequest(context, artifactFile).copy(
            fullPath = "/$REMOTE_HTML_CACHE_FULL_PATH"
        )
    }

    /**
     */
    override fun search(context: ArtifactSearchContext): List<Any> {
        val xmlString = context.request.reader.readXml()
        val remoteConfiguration = context.getRemoteConfiguration()
        val okHttpClient: OkHttpClient = createHttpClient(remoteConfiguration)
        val body = RequestBody.create("text/xml".toMediaTypeOrNull(), xmlString)
        val build: Request = Request.Builder().url("${remoteConfiguration.url}$XML_RPC_URI")
            .addHeader("Connection", "keep-alive")
            .post(body)
            .build()
        val htmlContent: String = okHttpClient.newCall(build).execute().body?.string()
            ?: throw PypiRemoteSearchException("search from ${remoteConfiguration.url} error")
        val methodResponse = XmlConvertUtil.xml2MethodResponse(htmlContent)
        return methodResponse.params.paramList[0].value.array?.data?.valueList ?: mutableListOf()
    }

    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile, storageCredentials: StorageCredentials?) {
        storageManager.storeArtifactFile(node, artifactFile, storageCredentials)
        artifactFile.delete()
        with(node) { logger.info("Success to store$projectId/$repoName/$fullPath") }
        logger.info("Success to insert $node")
    }

    override fun onDownloadResponse(context: ArtifactDownloadContext, response: Response): ArtifactResource {
        val artifactFile = createTempFile(response.body!!)
        val fileName = context.artifactInfo.getResponseName()
        try {
            val pypiInfo = ArtifactFileUtils.getPypiInfo(fileName, artifactFile)
            context.putAttribute(METADATA, pypiInfo)
        } catch (ignore: Exception) {
            logger.warn("Cannot resolve pypi package metadata of [$fileName]", ignore)
        }
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        val node = cacheArtifactFile(context, artifactFile)
        return ArtifactResource(
            artifactStream,
            context.artifactInfo.getResponseName(),
            RepositoryIdentify(context.projectId, context.repoName),
            node,
            ArtifactChannel.PROXY,
            context.useDisposition
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun onDownloadSuccess(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource,
        throughput: Throughput
    ) {
        context.getAttribute<PypiInfo>(METADATA)?.run {
            val fullPath = context.artifactInfo.getArtifactFullPath()
            val packageKey = PackageKeys.ofPypi(name)
            val existVersion = packageClient.findVersionByName(
                projectId = context.projectId,
                repoName = context.repoName,
                packageKey = packageKey,
                version = version
            ).data
            val packageMetadata = existVersion?.packageMetadata?.toMutableList() ?: mutableListOf()
            val artifactListMetadata = packageMetadata.find { it.key == ARTIFACT_LIST }
            val artifactList = artifactListMetadata?.value as? MutableList<String> ?: mutableListOf()
            artifactList.add(fullPath)
            if (artifactListMetadata == null) {
                packageMetadata.add(MetadataModel(key = ARTIFACT_LIST, value = artifactList))
            } else {
                artifactListMetadata.value = artifactList
            }
            packageClient.createVersion(
                PackageVersionCreateRequest(
                    projectId = context.projectId,
                    repoName = context.repoName,
                    packageName = name,
                    packageKey = packageKey,
                    packageType = PackageType.PYPI,
                    versionName = version,
                    size = artifactResource.getTotalSize(),
                    artifactPath = fullPath,
                    packageMetadata = packageMetadata,
                    overwrite = true,
                    createdBy = context.userId
                ),
                HttpContextHolder.getClientAddress()
            )
        }
        super.onDownloadSuccess(context, artifactResource, throughput)
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val node = artifactResource.node
                ?: nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            val metadata = node?.nodeMetadata
            val name = metadata?.find { it.key == NAME }?.value?.toString()
            val version = metadata?.find { it.key == VERSION }?.value?.toString()
            return if (!name.isNullOrBlank() && !version.isNullOrBlank()) {
                PackageDownloadRecord(projectId, repoName, PackageKeys.ofPypi(name), version)
            } else null
        }
    }

    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val nodeCreateRequest = super.buildCacheNodeCreateRequest(context, artifactFile)
        val metadataList = nodeCreateRequest.nodeMetadata?.toMutableList() ?: mutableListOf()
        return context.getAttribute<PypiInfo>(METADATA)?.run {
            metadataList.add(MetadataModel(key = NAME, value = name))
            metadataList.add(MetadataModel(key = VERSION, value = version))
            nodeCreateRequest.copy(nodeMetadata = metadataList)
        } ?: nodeCreateRequest
    }

    override fun remove(context: ArtifactRemoveContext) {
        val packageKey = HttpContextHolder.getRequest().getParameter(PACKAGE_KEY)
        val version = HttpContextHolder.getRequest().getParameter(VERSION)
        if (version.isNullOrBlank()) {
            // 删除包
            val versionList = packageClient.listAllVersion(
                projectId = context.projectId,
                repoName = context.repoName,
                packageKey = packageKey
            ).data.takeUnless { it.isNullOrEmpty() } ?: run {
                logger.warn("Remove pypi package: Cannot find any version of package [$packageKey]")
                return
            }
            versionList.forEach {
                deletePypiVersion(context, it, packageKey)
            }
        } else {
            // 删除版本
            val packageVersion = packageClient.findVersionByName(
                context.projectId,
                context.repoName,
                packageKey,
                version
            ).data ?: run {
                logger.warn("Remove pypi version: Cannot find version [$version] of package [$packageKey]")
                return
            }
            deletePypiVersion(context, packageVersion, packageKey)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deletePypiVersion(
        context: ArtifactRemoveContext,
        packageVersion: PackageVersion,
        packageKey: String
    ) {
        with(context) {
            val artifactList = packageVersion.packageMetadata.find { it.key == ARTIFACT_LIST }?.value as? List<String>
            if (!artifactList.isNullOrEmpty()) {
                artifactList.forEach {
                    nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, it, userId))
                }
            } else {
                packageVersion.contentPath?.let {
                    nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, it, userId))
                }
            }
            packageClient.deleteVersion(
                projectId, repoName, packageKey, packageVersion.name, HttpContextHolder.getClientAddress()
            )
        }
    }

    @Suppress("ReturnCount")
    private fun getVersionDetail(context: ArtifactQueryContext): Any? {
        val packageKey = context.request.getParameter(PACKAGE_KEY)
        val version = context.request.getParameter(VERSION)
        logger.info("Get version detail, packageKey: $packageKey, version: $version")
        val name = PackageKeys.resolvePypi(packageKey)
        val trueVersion = packageClient.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ).data
        val artifactPath = trueVersion?.contentPath ?: return null
        with(context.artifactInfo) {
            val node = nodeClient.getNodeDetail(projectId, repoName, artifactPath).data ?: return null
            val packageVersion = packageClient.findVersionByName(projectId, repoName, packageKey, version).data
            val count = packageVersion?.downloads ?: 0
            val pypiArtifactBasic = Basic(
                name,
                version,
                node.size, node.fullPath,
                node.createdBy, node.createdDate,
                node.lastModifiedBy, node.lastModifiedDate,
                count,
                node.sha256,
                node.md5,
                null,
                null
            )
            return PypiArtifactVersionData(pypiArtifactBasic, packageVersion?.packageMetadata)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PypiRemoteRepository::class.java)
    }
}
