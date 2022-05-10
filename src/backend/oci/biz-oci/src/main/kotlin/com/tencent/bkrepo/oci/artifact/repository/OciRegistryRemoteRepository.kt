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

package com.tencent.bkrepo.oci.artifact.repository

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.oci.constant.LAST_TAG
import com.tencent.bkrepo.oci.constant.MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.N
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.exception.OciForbiddenRequestException
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciTagArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.tags.TagsInfo
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.oci.util.OciLocationUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import java.net.URL
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OciRegistryRemoteRepository(
    private val ociOperationService: OciOperationService
) : RemoteRepository() {

    override fun upload(context: ArtifactUploadContext) {
        with(context) {
            val message = "Forbidden to upload chart into a remote repository [$projectId/$repoName]"
            logger.warn(message)
            throw OciForbiddenRequestException(message)
        }
    }

    /**
     * 生成远程构件下载url
     */
    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        val configuration = context.getRemoteConfiguration()
        val (fullpath, params) = when (context.artifactInfo) {
            is OciBlobArtifactInfo -> {
                with(context.artifactInfo as OciBlobArtifactInfo) {
                    Pair(OciLocationUtils.blobPathLocation(this.getDigest(), this), StringPool.EMPTY)
                }
            }
            is OciManifestArtifactInfo -> {
                with(context.artifactInfo as OciManifestArtifactInfo) {
                    Pair(OciLocationUtils.manifestPathLocation(this.reference, this), StringPool.EMPTY)
                }
            }
            is OciTagArtifactInfo -> {
                with(context.artifactInfo as OciTagArtifactInfo) {
                    val n = context.getAttribute<Int>(N)
                    val last = context.getAttribute<String>(LAST_TAG)
                    var param = StringPool.EMPTY
                    if (n != null) {
                        param += "n=$n"
                        if (!last.isNullOrBlank()) {
                            param += "&last=$last"
                        }
                    } else {
                        if (!last.isNullOrBlank()) {
                            param += "last=$last"
                        }
                    }
                    Pair("/$packageName/tags/list", param)
                }
            }
            else -> Pair(null, null)
        }
        val baseUrl = URL(configuration.url)
        val v2Url = URL(baseUrl, "/v2" + baseUrl.path)
        return UrlFormatter.format(v2Url.toString(), fullpath, params)
    }

/**
     * 远程下载响应回调
     */
    override fun onDownloadResponse(context: ArtifactDownloadContext, response: Response): ArtifactResource {
        val artifactFile = createTempFile(response.body()!!)
        val node = cacheArtifact(context, artifactFile)
        val digest = OciDigest.fromSha256(artifactFile.getFileSha256())
        val size = artifactFile.getSize()
        val mediaType = node?.metadata?.get(MEDIA_TYPE) ?: MediaTypes.APPLICATION_OCTET_STREAM
        logger.info(
            "The mediaType of Artifact ${context.artifactInfo.getArtifactFullPath()} " +
                "is $mediaType in repo: ${context.artifactInfo.getRepoIdentify()}"
        )

        val contentType = if (context.artifactInfo is OciManifestArtifactInfo) {
            node?.metadata?.get(MEDIA_TYPE) ?: OCI_IMAGE_MANIFEST_MEDIA_TYPE
        } else {
            MediaTypes.APPLICATION_OCTET_STREAM
        }
        OciResponseUtils.buildDownloadResponse(
            digest = digest,
            response = context.response,
            size = size,
            contentType = contentType.toString()
        )
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        val resource = ArtifactResource(
            inputStream = artifactStream,
            artifactName = context.artifactInfo.getResponseName(),
            node = node,
            channel = ArtifactChannel.LOCAL
        )
        resource.contentType = mediaType.toString()
        return resource
    }

    fun cacheArtifact(context: ArtifactDownloadContext, artifactFile: ArtifactFile): NodeDetail? {
        val configuration = context.getRemoteConfiguration()
        return if (configuration.cache.enabled) {
            val node = if (context.artifactInfo is OciBlobArtifactInfo) {
                ociOperationService.storeArtifact(
                    ociArtifactInfo = context.artifactInfo as OciBlobArtifactInfo,
                    artifactFile = artifactFile,
                    storageCredentials = context.storageCredentials
                )
            } else if (context.artifactInfo is OciManifestArtifactInfo) {
                ociOperationService.storeManifestArtifact(
                    ociArtifactInfo = context.artifactInfo as OciManifestArtifactInfo,
                    artifactFile = artifactFile,
                    storageCredentials = context.storageCredentials
                )
                updateManifestAndBlob(context, artifactFile)
                nodeClient.getNodeDetail(
                    projectId = context.artifactInfo.projectId,
                    repoName = context.artifactInfo.repoName,
                    fullPath = context.artifactInfo.getArtifactFullPath()
                ).data
            } else {
                null
            }
            node
        } else null
    }

    private fun updateManifestAndBlob(context: ArtifactDownloadContext, artifactFile: ArtifactFile) {
        with(context.artifactInfo as OciManifestArtifactInfo) {
            val digest = OciDigest.fromSha256(artifactFile.getFileSha256())
            // 上传manifest文件，同时需要将manifest中对应blob的属性进行补充到blob节点中，同时创建package相关信息
            ociOperationService.updateOciInfo(
                ociArtifactInfo = this,
                digest = digest,
                artifactFile = artifactFile,
                fullPath = context.artifactInfo.getArtifactFullPath(),
                storageCredentials = context.storageCredentials
            )
        }
    }

    /**
     * 远程下载响应回调
     */
    override fun onQueryResponse(context: ArtifactQueryContext, response: Response): Any? {
        val body = response.body()!!
        val artifactFile = createTempFile(body)
        val size = artifactFile.getSize()
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        artifactFile.delete()
        return JsonUtils.objectMapper.readValue(artifactStream, TagsInfo::class.java)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(OciRegistryRemoteRepository::class.java)
    }
}
