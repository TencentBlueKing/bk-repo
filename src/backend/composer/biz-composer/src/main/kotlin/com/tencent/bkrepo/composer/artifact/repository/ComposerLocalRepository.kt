/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.  
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.composer.artifact.repository

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.UnsupportedMethodException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.composer.COMPOSER_VERSION_INIT
import com.tencent.bkrepo.composer.INIT_PACKAGES
import org.springframework.stereotype.Component
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.composer.DIRECT_DISTS
import com.tencent.bkrepo.composer.exception.ComposerArtifactMetadataException
import com.tencent.bkrepo.composer.util.DecompressUtil.wrapperJson
import com.tencent.bkrepo.composer.util.JsonUtil
import com.tencent.bkrepo.composer.util.JsonUtil.wrapperJson
import com.tencent.bkrepo.composer.util.JsonUtil.wrapperPackageJson
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import com.tencent.bkrepo.composer.pojo.ArtifactVersionDetail
import com.tencent.bkrepo.composer.pojo.Basic
import com.tencent.bkrepo.composer.util.pojo.ComposerArtifact
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.lang.RuntimeException

@Component
class ComposerLocalRepository : LocalRepository() {

    //默认值为方便本地调试
    //服务域名,例：bkrepo.com
    @Value("\${bkrepo.host:127.0.0.1}")
    private val bkrepoHost: String = ""

    //服务端口识别字段，例 8083或composer
    @Value("\${bkrepo.composer.port:8083}")
    private val composerPort: String = ""

    @Autowired
    lateinit var packageClient: PackageClient

    @Autowired
    lateinit var stageClient: StageClient

    /**
     * Composer节点创建请求
     */
    fun getCompressNodeCreateRequest(
            context: ArtifactUploadContext,
            metadata: MutableMap<String, String>
    ): NodeCreateRequest {
        val nodeCreateRequest = buildNodeCreateRequest(context)
        return nodeCreateRequest.copy(
                fullPath = "/$DIRECT_DISTS${context.artifactInfo.getArtifactFullPath()}",
                overwrite = true,
                metadata = metadata
        )
    }

    private fun updateIndex(composerArtifact: ComposerArtifact, context: ArtifactContext) {
        // 查询对应的 "/p/%package%.json" 是否存在
        val pArtifactUri = "/p/${composerArtifact.name}.json"
        val node = nodeClient.getNodeDetail(context.projectId, context.repoName, pArtifactUri).data
        val resultJson = if (node == null) {
            with(composerArtifact) {
                JsonUtil.addComposerVersion(String.format(COMPOSER_VERSION_INIT, name), json, name, version)
            }
        } else {
            nodeToJson(node).let { with(composerArtifact) {
                    JsonUtil.addComposerVersion(it, json, name, version) }
            }
        }
        val jsonFile = ByteArrayInputStream(resultJson.toByteArray()).use {
            ArtifactFileFactory.build(it)
        }
        val nodeCreateRequest = createNode(context, pArtifactUri, jsonFile)
        store(nodeCreateRequest, jsonFile, context.storageCredentials)
    }

    fun createNode(context: ArtifactContext, fullPath: String?, artifactFile: ArtifactFile): NodeCreateRequest {
        return NodeCreateRequest(
                context.projectId,
                context.repoName,
                fullPath ?: context.artifactInfo.getArtifactFullPath(),
                false,
                0L,
                true,
                artifactFile.getSize(),
                artifactFile.getFileSha256(),
                artifactFile.getFileMd5(),
                null,
                context.userId
        )
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun onUpload(context: ArtifactUploadContext) {
        //读取需要保存数据
        val composerArtifact = context.getArtifactFile().getInputStream().use {
            it.wrapperJson(context.artifactInfo.getArtifactFullPath())
        }
        //保存节点
        val metadata = mutableMapOf<String, String>()
        metadata["packageKey"] = PackageKeys.ofComposer(composerArtifact.name)
        metadata["version"] = composerArtifact.version
        val nodeCreateRequest = getCompressNodeCreateRequest(context, metadata)
        store(nodeCreateRequest, context.getArtifactFile(), context.storageCredentials)

        //保存版本信息
        packageClient.createVersion(
                PackageVersionCreateRequest(
                        context.projectId,
                        context.repoName,
                        composerArtifact.name,
                        PackageKeys.ofComposer(composerArtifact.name),
                        PackageType.RPM,
                        null,
                        composerArtifact.version,
                        context.getArtifactFile().getSize(),
                        null,
                        "/$DIRECT_DISTS${context.artifactInfo.getArtifactFullPath()}",
                        null,
                        metadata,
                        overwrite = true,
                        createdBy = context.userId
                )
        )
        //更新索引
        updateIndex(composerArtifact, context)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun remove(context: ArtifactRemoveContext) {
        val packageKey = HttpContextHolder.getRequest().getParameter("packageKey")
        val version = HttpContextHolder.getRequest().getParameter("version")
        if (version.isNullOrBlank()) {
            // 删除包
            val pages = packageClient.listVersionPage(
                    context.projectId,
                    context.repoName,
                    packageKey,
                    null,
                    null,
                    0,
                    1000
            ).data?.records ?: return
            for (packageVersion in pages) {
                val node = nodeClient.getNodeDetail(context.projectId, context.repoName, packageVersion.contentPath!!).data
                        ?: continue
                removeComposerArtifact(node, packageKey, packageVersion.name, context)
            }
        } else {
            with(context.artifactInfo) {
                val packageVersion = packageClient.findVersionByName(
                        context.projectId,
                        context.repoName,
                        packageKey,
                        version
                ).data ?: return
                val node = nodeClient.getNodeDetail(projectId, repoName, packageVersion.contentPath!!).data ?: return
                removeComposerArtifact(node, packageKey, version, context)
            }
        }
    }

    private fun getPackageJson(context: ArtifactContext, name: String): String? {
        val jsonPath = "/p/$name.json"
        val node = nodeClient.getNodeDetail(context.projectId, context.repoName, jsonPath).data?: return null
        return nodeToJson(node)
    }

    /**
     * 删除包json中对应的版本
     */
    private fun deleteJsonVersion(context: ArtifactRemoveContext, name: String, version: String) {
        val jsonPath = "/p/$name.json"
        val packageJson = getPackageJson(context, name)?: return

        val resultJson = JsonUtil.deleteComposerVersion(packageJson, name, version)

        val jsonFile = ByteArrayInputStream(resultJson.toByteArray()).use {
            ArtifactFileFactory.build(it)
        }
        val nodeCreateRequest = createNode(context, jsonPath, jsonFile)
        store(nodeCreateRequest, jsonFile, context.storageCredentials)
    }

    /**
     * 删除composer构件
     */
    fun removeComposerArtifact(
            node: NodeDetail,
            packageKey: String,
            version: String,
            context: ArtifactRemoveContext
    ) {
        if (node.folder) {
            throw UnsupportedMethodException("Delete folder is forbidden")
        }
        with(context) {
            // 更新索引
            deleteJsonVersion(context, PackageKeys.resolveComposer(packageKey), version)
            val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, node.fullPath, context.userId)
            nodeClient.deleteNode(nodeDeleteRequest)
            logger.info("Success to delete node $nodeDeleteRequest")
            deleteVersion(projectId, repoName, packageKey, version)
            logger.info("Success to delete version $projectId | $repoName : $packageKey $version")
        }
    }

    /**
     * 删除版本后，检查该包下是否还有包。
     */
    fun deleteVersion(projectId: String, repoName: String, packageKey: String, version: String) {
        packageClient.deleteVersion(projectId, repoName, packageKey, version)
        val page = packageClient.listVersionPage(projectId, repoName, packageKey).data ?: return
        if (page.records.isEmpty()) packageClient.deletePackage(projectId, repoName, packageKey)
    }

    /**
     * 查询对应请求包名的'*.json'文件
     */
    fun getJson(context: ArtifactQueryContext): String? {
        with(context.artifactInfo) {
            return if (getArtifactFullPath().matches(Regex("^/p/(.*)\\.json$"))) {
                val host = getHost(context)
                val packageName = getArtifactFullPath().removePrefix("/p/").removeSuffix(".json")
                stream2Json(context)?.wrapperJson(host, packageName)
            } else {
                null
            }
        }
    }

    fun getHost(context: ArtifactContext): String {
        val request = HttpContextHolder.getRequest()
        val scheme = request.scheme
        val servletPath = request.servletPath.removeSuffix(context.artifactInfo.getArtifactFullPath())
        return "$scheme://$bkrepoHost/$composerPort$servletPath"
    }

    /**
     * 返回 /packages.json
     */
    fun getPackages(context: ArtifactQueryContext): String {
        val host = getHost(context)
        val node = with(context.artifactInfo) {
            nodeClient.getNodeDetail(projectId, repoName, getArtifactFullPath()).data
        }
        return if (node == null) {
            val artifactFile = ByteArrayInputStream(INIT_PACKAGES.toByteArray()).use {
                ArtifactFileFactory.build(it)
            }
            val nodeCreateRequest = createNode(context, null, artifactFile)
            store(nodeCreateRequest, artifactFile, context.storageCredentials)
            return INIT_PACKAGES.wrapperPackageJson(host)
        } else {
            nodeToJson(node).wrapperPackageJson(host)
        }
    }

    override fun query(context: ArtifactQueryContext): Any? {
        return if (context.artifactInfo.getArtifactFullPath() == "/packages.json") {
            getPackages(context)
        } else if (context.artifactInfo.getArtifactFullPath().startsWith("/ext/version/detail/")) {
            getVersionDetail(context)
        } else {
            getJson(context)
        }
    }

    private fun nodeToJson(node: NodeDetail): String {
        val inputStream = storageService.load(
                node.sha256!!,
                Range.full(node.size),
                null
        ) ?: throw RuntimeException("load ${node.projectId} | ${node.repoName} | ${node.fullPath} error")
        val stringBuilder = StringBuilder()
        var line: String?
        try {
            BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
                while ((bufferedReader.readLine().also { line = it }) != null) {
                    stringBuilder.append(line)
                }
            }
        } finally {
            inputStream.closeQuietly()
        }
        return stringBuilder.toString()
    }

    /**
     * 加载搜索到的流并返回内容
     */
    private fun stream2Json(context: ArtifactContext): String? {
        return with(context.artifactInfo) {
            val node = nodeClient.getNodeDetail(projectId, repoName, getArtifactFullPath()).data ?: return null
            nodeToJson(node)
        }
    }

    /**
     * composer 客户端下载统计
     */
    override fun buildDownloadRecord(
            context: ArtifactDownloadContext,
            artifactResource: ArtifactResource
    ): DownloadStatisticsAddRequest? {
        with(context) {
            val fullPath = context.artifactInfo.getArtifactFullPath()
            val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data ?: return null
            val packageKey = node.metadata["packageKey"] ?: throw ComposerArtifactMetadataException(
                    "${artifactInfo.getArtifactFullPath()} : not found metadata.packageKay value"
            )
            val version = node.metadata["version"] ?: throw ComposerArtifactMetadataException(
                    "${artifactInfo.getArtifactFullPath()} : not found metadata.version value"
            )
            val name = PackageKeys.resolveComposer(packageKey.toString())
            return if (fullPath.endsWith("")) {
                return DownloadStatisticsAddRequest(
                        projectId, repoName,
                        packageKey.toString(), name, version.toString()
                )
            } else {
                null
            }
        }
    }

    /**
     * 版本详情
     */
    fun getVersionDetail(context: ArtifactQueryContext): Any? {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        val name = PackageKeys.resolveComposer(packageKey)
        val trueVersion = packageClient.findVersionByName(
                context.projectId,
                context.repoName,
                packageKey,
                version
        ).data ?: return null
        val artifactPath = trueVersion.contentPath ?: return null
        with(context.artifactInfo) {
            val jarNode = nodeClient.getNodeDetail(
                    projectId, repoName, artifactPath
            ).data ?: return null
            val stageTag = stageClient.query(projectId, repoName, packageKey, version).data
            val rpmArtifactMetadata = jarNode.metadata
            val packageVersion = packageClient.findVersionByName(
                    projectId, repoName, packageKey, version
            ).data
            val count = packageVersion?.downloads ?: 0
            val composerArtifactBasic = Basic(
                    name,
                    version,
                    jarNode.size, jarNode.fullPath,
                    jarNode.createdBy, jarNode.createdDate,
                    jarNode.lastModifiedBy, jarNode.lastModifiedDate,
                    count,
                    jarNode.sha256,
                    jarNode.md5,
                    stageTag,
                    null
            )
            return ArtifactVersionDetail(composerArtifactBasic, rpmArtifactMetadata)
        }
    }

    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile, storageCredentials: StorageCredentials?): Boolean {
        try {
            storageManager.storeArtifactFile(node, artifactFile, storageCredentials)
        } catch (exception: Exception) {
            return false
        }
        try {
            artifactFile.delete()
            with(node) { logger.info("Success to store$projectId/$repoName/$fullPath") }
            logger.info("Success to insert $node")
        } catch (exception: Exception) {
            //报异常不会影响服务，不做处理
            logger.error(exception.message)
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ComposerLocalRepository::class.java)
    }
}
