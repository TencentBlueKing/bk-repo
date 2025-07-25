/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.api.util.toYamlString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.SOURCE_TYPE
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.FileNameParser
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.config.HelmProperties
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.FILE_TYPE
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.HelmMessageCode
import com.tencent.bkrepo.helm.constants.INDEX_YAML
import com.tencent.bkrepo.helm.constants.META_DETAIL
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.PROXY_URL
import com.tencent.bkrepo.helm.constants.SIZE
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.exception.HelmForbiddenRequestException
import com.tencent.bkrepo.helm.pojo.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.service.impl.HelmOperationService
import com.tencent.bkrepo.helm.utils.ChartParserUtil
import com.tencent.bkrepo.helm.utils.HelmMetadataUtils
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.helm.utils.ObjectBuilderUtil
import com.tencent.bkrepo.repository.constant.PROXY_DOWNLOAD_URL
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HelmRemoteRepository(
    private val helmOperationService: HelmOperationService,
    private val helmProperties: HelmProperties
) : RemoteRepository() {

    override fun upload(context: ArtifactUploadContext) {
        with(context) {
            val message = "Forbidden to upload chart into a remote repository [$projectId/$repoName]"
            logger.warn(message)
            throw HelmForbiddenRequestException(HelmMessageCode.HELM_FILE_UPLOAD_FORBIDDEN, "$projectId/$repoName")
        }
    }

    /**
     * 远程下载响应回调
     */
    override fun onQueryResponse(context: ArtifactQueryContext, response: Response): Any? {
        logger.info("on remote query response...")
        val body = response.body!!
        val tempFile = createTempFile(body)
        val artifactFile = buildNewIndex(context, tempFile)
        val size = artifactFile.getSize()
        val result = checkNode(context, artifactFile)
        if (result == null) {
            logger.info("store the new helm file to replace the old version..")
            parseAttribute(context, artifactFile)
            val stream = artifactFile.getInputStream().artifactStream(Range.full(size))
            cacheArtifactFile(context, artifactFile)
            return stream
        }
        return result
    }

    /**
     * 针对remote类型的仓库，index.yaml每次都是去远程拉取，所以需要修改其urls
     */
    private fun buildNewIndex(context: ArtifactContext, artifactFile: ArtifactFile): ArtifactFile {
        val fullPath = context.getStringAttribute(FULL_PATH)!!
        if (fullPath != HelmUtils.getIndexCacheYamlFullPath()) return artifactFile
        val size = artifactFile.getSize()
        val helmIndexYamlMetadata = artifactFile.getInputStream().artifactStream(Range.full(size)).use {
            it.readYamlString() as HelmIndexYamlMetadata
        }
        helmOperationService.buildChartUrl(
            domain = helmProperties.domain,
            projectId = context.projectId,
            repoName = context.repoName,
            helmIndexYamlMetadata = helmIndexYamlMetadata
        )
        return ArtifactFileFactory.build(helmIndexYamlMetadata.toYamlString().byteInputStream())
    }

    /**
     * 如缓存存在，判断缓存文件和最新文件是否一样，如不一样以最新文件为准
     */
    private fun checkNode(context: ArtifactQueryContext, artifactFile: ArtifactFile): Any? {
        with(context) {
            val fullPath = getStringAttribute(FULL_PATH)!!
            logger.info(
                "Will go to check the artifact $fullPath in the cache " +
                    "in repo ${context.artifactInfo.getRepoIdentify()}"
            )
            val sha256 = artifactFile.getFileSha256()
            nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))?.let {
                if (it.sha256.equals(sha256)) {
                    logger.info("artifact [$fullPath] hits the cache.")
                    return artifactFile.getInputStream().artifactStream(Range.full(artifactFile.getSize()))
                }
            }
            return null
        }
    }

    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val fullPath = if (context.getStringAttribute(FULL_PATH).isNullOrBlank()) {
            val temp = HelmUtils.convertIndexYamlPathToCache(context.artifactInfo.getArtifactFullPath())
            context.putAttribute(FULL_PATH, temp)
            temp
        } else {
            context.getStringAttribute(FULL_PATH)
        }
        logger.info("create remote download url with path $fullPath...")
        val remoteConfiguration = context.getRemoteConfiguration()
        val remoteDomain = remoteConfiguration.url.trimEnd('/')
        return when (fullPath) {
            HelmUtils.getIndexCacheYamlFullPath() -> INDEX_REQUEST_URL.format(remoteDomain, INDEX_YAML)
            else -> buildDownloadUrl(context, fullPath, remoteDomain)
        }
    }

    private fun buildDownloadUrl(context: ArtifactContext, fullPath: String?, remoteDomain: String): String {
        val map = FileNameParser.parseNameAndVersionWithRegex(fullPath!!)
        val name = map[NAME].toString()
        val version = map[VERSION].toString()
        val packageVersion = packageService.findVersionByName(
            projectId = context.projectId,
            repoName = context.repoName,
            packageKey = PackageKeys.ofHelm(name),
            versionName = version
        )
        var downloadUrl = CHART_REQUEST_URL.format(remoteDomain, fullPath)
        if (packageVersion != null) {
            val proxyDownloadUrl = packageVersion.metadata[PROXY_DOWNLOAD_URL]?.toString()
            if (proxyDownloadUrl != null) {
                downloadUrl = if (!proxyDownloadUrl.contains(remoteDomain)) {
                    remoteDomain + StringPool.SLASH + proxyDownloadUrl
                } else {
                    proxyDownloadUrl
                }
            }
        }
        logger.info("remote chart download url is $downloadUrl")
        return downloadUrl
    }

    /**
     * 远程下载响应回调
     */
    override fun onDownloadResponse(
        context: ArtifactDownloadContext,
        response: Response,
        useDisposition: Boolean,
        syncCache: Boolean
    ): ArtifactResource {
        val tempFile = createTempFile(response.body!!)
        val artifactFile = buildNewIndex(context, tempFile)
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        parseAttribute(context, artifactFile)
        val node = cacheArtifactFile(context, artifactFile)
        // 代理下载的制品需要在创建packageversion时标注来源
        context.putAttribute(SOURCE_TYPE, ArtifactChannel.PROXY)
        helmOperationService.initPackageInfo(context)
        return ArtifactResource(
            inputStream = artifactStream,
            artifactName = context.artifactInfo.getResponseName(),
            node = node,
            channel = ArtifactChannel.PROXY,
            useDisposition = context.useDisposition
        )
    }

    private fun parseAttribute(context: ArtifactContext, artifactFile: ArtifactFile) {
        val size = artifactFile.getSize()
        context.putAttribute(SIZE, size)
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        when (context.getStringAttribute(FILE_TYPE)) {
            CHART -> {
                val helmChartMetadata = ChartParserUtil.parseChartInputStream(artifactStream)
                helmChartMetadata.let {
                    context.putAttribute(NAME, it.name)
                    context.putAttribute(VERSION, it.version)
                    context.putAttribute(META_DETAIL, HelmMetadataUtils.convertToMap(helmChartMetadata))
                }
            }
            PROV -> ChartParserUtil.parseNameAndVersion(context)
        }
    }

    /**
     * 获取缓存节点创建请求
     */
    override fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val metadata: MutableMap<String, Any> = context.getAttribute(META_DETAIL) ?: mutableMapOf()
        if (context.getStringAttribute(FILE_TYPE) != null) {
            val remoteConfiguration = context.getRemoteConfiguration()
            metadata[PROXY_URL] = remoteConfiguration.url
        }

        return NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            folder = false,
            fullPath = HelmUtils.convertIndexYamlPathToCache(context.getStringAttribute(FULL_PATH)!!),
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            operator = context.userId,
            nodeMetadata = metadata.map { MetadataModel(key = it.key, value = it.value) },
            overwrite = true
        )
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        return ObjectBuilderUtil.buildDownloadRecordRequest(context)
    }

    /**
     * 删除本地缓存chart包
     */
    override fun remove(context: ArtifactRemoveContext) {
        helmOperationService.removeChartOrProv(context)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmRemoteRepository::class.java)
        const val CHART_REQUEST_URL = "%s/charts%s"
        const val INDEX_REQUEST_URL = "%s/%s"
    }
}
