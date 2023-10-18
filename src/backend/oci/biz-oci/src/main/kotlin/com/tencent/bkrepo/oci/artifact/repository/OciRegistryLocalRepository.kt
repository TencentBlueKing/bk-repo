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

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.innercos.http.HttpMethod
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.message.StorageMessageCode
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.oci.artifact.OciRegistryArtifactConfigurer
import com.tencent.bkrepo.oci.constant.EMPTY_FILE_SHA256
import com.tencent.bkrepo.oci.constant.FORCE
import com.tencent.bkrepo.oci.constant.IMAGE_VERSION
import com.tencent.bkrepo.oci.constant.LAST_TAG
import com.tencent.bkrepo.oci.constant.MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.N
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.OLD_DOCKER_MEDIA_TYPE
import com.tencent.bkrepo.oci.constant.OciMessageCode
import com.tencent.bkrepo.oci.constant.PATCH
import com.tencent.bkrepo.oci.constant.POST
import com.tencent.bkrepo.oci.exception.OciBadRequestException
import com.tencent.bkrepo.oci.exception.OciFileNotFoundException
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciTagArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.response.CatalogResponse
import com.tencent.bkrepo.oci.pojo.response.ResponseProperty
import com.tencent.bkrepo.oci.pojo.tags.TagsInfo
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.oci.util.OciLocationUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.oci.util.OciUtils
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OciRegistryLocalRepository(
    private val ociOperationService: OciOperationService,
    private val artifactConfigurerSupport: OciRegistryArtifactConfigurer
) : LocalRepository() {

    /**
     * 上传前回调
     */
    override fun onUploadBefore(context: ArtifactUploadContext) {
        with(context) {
            super.onUploadBefore(context)
            val requestMethod = request.method
            if (PATCH == requestMethod) {
                logger.info("Will using patch ways to upload file in repo ${artifactInfo.getRepoIdentify()}")
                return
            }
            val isForce = request.getParameter(FORCE)?.let { true } ?: false
            val projectId = repositoryDetail.projectId
            val repoName = repositoryDetail.name
            val fullPath = context.artifactInfo.getArtifactFullPath()
            val isExist = nodeClient.checkExist(projectId, repoName, fullPath).data!!
            logger.info(
                "The file $fullPath that will be uploaded to server is exist: $isExist " +
                    "in repo ${artifactInfo.getRepoIdentify()}, and the flag of force overwrite is $isForce"
            )
            if (isExist && !isForce) {
                logger.warn(
                    "${fullPath.trimStart('/')} already exists in repo ${artifactInfo.getRepoIdentify()}"
                )
                return
            }
        }
    }

    /**
     * 从Content-Range头中解析出起始位置
     */
    private fun getRangeInfo(range: String): Pair<Long, Long> {
        val values = range.split("-")
        return Pair(values[0].toLong(), values[1].toLong())
    }

    /**
     * 上传
     */
    override fun onUpload(context: ArtifactUploadContext) {
        logger.info("Preparing to upload the oci file in repo ${context.artifactInfo.getRepoIdentify()}")
        val responseProperty = when (context.request.method) {
            PATCH -> patchUpload(context)
            POST -> postUpload(context)
            else -> putUpload(context)
        } ?: return
        val domain = ociOperationService.getReturnDomain(HttpContextHolder.getRequest())
        OciResponseUtils.buildUploadResponse(domain, responseProperty, context.response)
    }

    /**
     * blob chunks上传中的patch上传部分逻辑处理
     * Pushing a blob in chunks
     * A chunked blob upload is accomplished in three phases:
     * 1:Obtain a session ID (upload URL) (POST)
     * 2:Upload the chunks (PATCH)
     * 3:Close the session (PUT)
     */
    private fun patchUpload(context: ArtifactUploadContext): ResponseProperty? {
        logger.info("Will using patch ways to upload file in repo ${context.artifactInfo.getRepoIdentify()}")
        if (context.artifactInfo !is OciBlobArtifactInfo) return null
        with(context.artifactInfo as OciBlobArtifactInfo) {
            val range = context.request.getHeader("Content-Range")
            val length = context.request.contentLength
            if (!range.isNullOrEmpty() && length > -1) {
                logger.info("range $range, length $length, uuid $uuid")
                val (start, end) = getRangeInfo(range)
                // 判断要上传的长度是否超长
                if (end - start > length - 1) {
                    return ResponseProperty(
                        location = OciLocationUtils.blobUUIDLocation(uuid!!, this),
                        status = HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        range = length.toLong(),
                        uuid = uuid!!,
                        contentLength = 0
                    )
                }
            }
            val patchLen = storageService.append(
                appendId = uuid!!,
                artifactFile = context.getArtifactFile(),
                storageCredentials = context.repositoryDetail.storageCredentials
            )
            return ResponseProperty(
                location = OciLocationUtils.blobUUIDLocation(uuid!!, this),
                status = HttpStatus.ACCEPTED,
                range = patchLen,
                uuid = uuid!!,
                contentLength = 0
            )
        }
    }

    /**
     * blob 上传，直接使用post
     * Pushing a blob monolithically ：A single POST request
     */
    private fun postUpload(context: ArtifactUploadContext): ResponseProperty? {
        val artifactFile = context.getArtifactFile()
        val digest = OciDigest.fromSha256(artifactFile.getFileSha256())
        ociOperationService.storeArtifact(
            ociArtifactInfo = context.artifactInfo as OciArtifactInfo,
            artifactFile = artifactFile,
            storageCredentials = context.storageCredentials
        )
        val blobLocation = OciLocationUtils.blobLocation(digest, context.artifactInfo as OciArtifactInfo)
        logger.info(
            "Artifact ${context.artifactInfo.getArtifactFullPath()} has " +
                "been uploaded to ${context.artifactInfo.getArtifactFullPath()}, " +
                "and will can be accessed in $blobLocation" +
                " in repo  ${context.artifactInfo.getRepoIdentify()}"
        )
        return ResponseProperty(
            digest = digest,
            location = blobLocation,
            status = HttpStatus.CREATED,
            contentLength = 0
        )
    }

    /**
     * put 上传包含三种逻辑：
     * 1 blob POST with PUT 上传的put模块处理
     * 2 blob POST PATCH with PUT 上传的put模块处理
     * 3 manifest PUT上传的逻辑处理
     */
    private fun putUpload(context: ArtifactUploadContext): ResponseProperty? {
        return when (context.artifactInfo) {
            is OciBlobArtifactInfo -> putUploadBlob(context)
            is OciManifestArtifactInfo -> putUploadManifest(context)
            else -> null
        }
    }

    /**
     * blob PUT上传的逻辑处理
     * 1 blob POST with PUT 上传的put模块处理
     * 2 blob POST PATCH with PUT 上传的put模块处理
     */
    private fun putUploadBlob(context: ArtifactUploadContext): ResponseProperty {
        val artifactInfo = context.artifactInfo as OciBlobArtifactInfo
        val sha256 = artifactInfo.getDigestHex()
        val fileInfo = try {
            storageService.append(
                appendId = artifactInfo.uuid!!,
                artifactFile = context.getArtifactFile(),
                storageCredentials = context.repositoryDetail.storageCredentials
            )
            val fileInfo = storageService.finishAppend(artifactInfo.uuid!!, context.repositoryDetail.storageCredentials)
            if (fileInfo.sha256 != sha256)
                throw OciBadRequestException(OciMessageCode.OCI_DIGEST_INVALID, sha256)
            // 当并发情况下文件被删可能导致文件size为0
            if (fileInfo.size == 0L && fileInfo.sha256 != EMPTY_FILE_SHA256)
                throw StorageErrorException(StorageMessageCode.STORE_ERROR)
            ociOperationService.storeArtifact(
                ociArtifactInfo = context.artifactInfo as OciArtifactInfo,
                artifactFile = context.getArtifactFile(),
                storageCredentials = context.storageCredentials,
                fileInfo = fileInfo
            )
            fileInfo
        } catch (e: StorageErrorException) {
            // 计算sha256和转存文件导致时间较长，会出现请求超时，然后发起重试，导致并发操作该临时文件，文件可能已经被删除
            if (storageService.exist(sha256, context.repositoryDetail.storageCredentials)) {
                val nodeDetail = nodeClient.getNodeDetail(
                    artifactInfo.projectId, artifactInfo.repoName, artifactInfo.getArtifactFullPath()
                ).data
                if (nodeDetail == null || nodeDetail.sha256 != sha256) {
                    throw e
                } else {
                    FileInfo(nodeDetail.sha256!!, nodeDetail.md5!!, nodeDetail.size)
                }
            } else {
                throw e
            }
        }
        val digest = OciDigest.fromSha256(fileInfo.sha256)
        val blobLocation = OciLocationUtils.blobLocation(digest, artifactInfo)
        logger.info(
            "Artifact ${context.artifactInfo.getArtifactFullPath()} " +
                "has been uploaded to ${context.artifactInfo.getArtifactFullPath()}" +
                "and will can be accessed in $blobLocation" +
                " in repo  ${context.artifactInfo.getRepoIdentify()}"
        )
        return ResponseProperty(
            digest = digest,
            location = blobLocation,
            status = HttpStatus.CREATED,
            contentLength = 0
        )
    }

    /**
     * manifest文件 PUT上传的逻辑处理
     */
    private fun putUploadManifest(context: ArtifactUploadContext): ResponseProperty {
        val artifactInfo = context.artifactInfo as OciManifestArtifactInfo
        val artifactFile = context.getArtifactFile()
        val digest = OciDigest.fromSha256(artifactFile.getFileSha256())
        val node = ociOperationService.storeArtifact(
            ociArtifactInfo = artifactInfo,
            artifactFile = artifactFile,
            storageCredentials = context.storageCredentials
        )
        // 上传manifest文件，同时需要判断manifest中的blob节点是否已经存在，同时创建package相关信息
        ociOperationService.updateOciInfo(
            ociArtifactInfo = artifactInfo,
            digest = digest,
            storageCredentials = context.storageCredentials,
            nodeDetail = node!!
        )
        val manifestLocation = OciLocationUtils.manifestLocation(digest, artifactInfo)
        logger.info(
            "Artifact ${context.artifactInfo.getArtifactFullPath()} has been uploaded to ${node.fullPath}" +
                "and will can be accessed in $manifestLocation" +
                " in repo  ${context.artifactInfo.getRepoIdentify()}"
        )
        return ResponseProperty(
            digest = digest,
            location = manifestLocation,
            status = HttpStatus.CREATED,
            contentLength = 0
        )
    }

    /**
     * 在原有逻辑上增加响应头
     */
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        logger.info(
            "Will start to download oci artifact ${context.artifactInfo.getArtifactFullPath()}" +
                " in repo ${context.artifactInfo.getRepoIdentify()}..."
        )
        val artifactInfo = context.artifactInfo as OciArtifactInfo
        val fullPath = ociOperationService.getNodeFullPath(artifactInfo)
        return downloadArtifact(context, fullPath)
    }

    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        val artifactInfo = context.artifactInfo as OciArtifactInfo
        if (context.artifactInfo !is OciManifestArtifactInfo) return null
        if (context.request.method == HttpMethod.HEAD.name) {
            return null
        }
        val version = artifactResource.node?.metadata?.get(IMAGE_VERSION)?.toString() ?: return null
        return PackageDownloadRecord(
            projectId = context.projectId,
            repoName = context.repoName,
            packageKey = PackageKeys.ofName(context.repo.type, artifactInfo.packageName),
            packageVersion = version
        )
    }

    /**
     * 针对oci协议 需要将对应的media type返回
     */
    private fun downloadArtifact(context: ArtifactDownloadContext, fullPath: String?): ArtifactResource? {
        if (fullPath == null) return null
        val node = getNodeDetail(context.artifactInfo as OciArtifactInfo, fullPath)
        // 拦截制品下载
        node?.let {
            downloadIntercept(context, it)
            packageVersion(context, it)?.let { packageVersion -> downloadIntercept(context, packageVersion) }
        }
        logger.info(
            "Starting to download $fullPath " +
                "in repo: ${context.artifactInfo.getRepoIdentify()}"
        )
        val inputStream = storageManager.loadArtifactInputStream(node, context.storageCredentials)
            ?: return null
        val digest = OciDigest.fromSha256(node!!.sha256.orEmpty())
        val mediaType = node.metadata[MEDIA_TYPE] ?: run {
            node.metadata[OLD_DOCKER_MEDIA_TYPE] ?: MediaTypes.APPLICATION_OCTET_STREAM
        }
        val contentType = if (context.artifactInfo is OciManifestArtifactInfo) {
            node.metadata[MEDIA_TYPE] ?: run {
                node.metadata[OLD_DOCKER_MEDIA_TYPE] ?: OCI_IMAGE_MANIFEST_MEDIA_TYPE
            }
        } else {
            MediaTypes.APPLICATION_OCTET_STREAM
        }

        logger.info(
            "The mediaType of Artifact $fullPath is $mediaType and it's contentType is $contentType" +
                "in repo: ${context.artifactInfo.getRepoIdentify()}"
        )
        OciResponseUtils.buildDownloadResponse(
            digest = digest,
            response = context.response,
            size = node.size,
            contentType = contentType as String
        )
        val resource = ArtifactResource(
            inputStream = inputStream,
            artifactName = context.artifactInfo.getResponseName(),
            node = node,
            channel = ArtifactChannel.LOCAL,
            useDisposition = context.useDisposition
        )
        resource.contentType = mediaType.toString()
        return resource
    }

    private fun getNodeDetail(artifactInfo: OciArtifactInfo, fullPath: String): NodeDetail? {
        artifactInfo.setArtifactMappingUri(fullPath)
        return ArtifactContextHolder.getNodeDetail(artifactInfo) ?: run {
            val oldDockerPath = ociOperationService.getDockerNode(artifactInfo)
                ?: return null
            artifactInfo.setArtifactMappingUri(oldDockerPath)
            ArtifactContextHolder.getNodeDetail(artifactInfo)
        }
    }

    /**
     * 版本不存在时 status code 404
     */
    override fun remove(context: ArtifactRemoveContext) {
        with(context.artifactInfo) {
            val fullPath = ociOperationService.getNodeFullPath(this as OciArtifactInfo)
                ?: throw OciFileNotFoundException(
                    OciMessageCode.OCI_FILE_NOT_FOUND, getArtifactFullPath(), getRepoIdentify()
                )
            nodeClient.getNodeDetail(projectId, repoName, fullPath).data
                ?: throw OciFileNotFoundException(
                    OciMessageCode.OCI_FILE_NOT_FOUND, getArtifactFullPath(), getRepoIdentify()
                )
            logger.info("Ready to delete $fullPath in repo ${getRepoIdentify()}")
            val request = NodeDeleteRequest(projectId, repoName, fullPath, context.userId)
            nodeClient.deleteNode(request)
            OciResponseUtils.buildDeleteResponse(context.response)
        }
    }

    override fun query(context: ArtifactQueryContext): Any? {
        if (context.artifactInfo is OciTagArtifactInfo) {
            val packageName = (context.artifactInfo as OciTagArtifactInfo).packageName
            return if (packageName.isBlank()) {
                // 查询catalog
                queryCatalog(context)
            } else {
                // 查询tag列表
                queryTagList(context)
            }
        }
        if (context.artifactInfo is OciManifestArtifactInfo) {
            // 查询manifest文件内容
            return queryManifest(context)
        }
        return null
    }

    private fun packageVersion(context: ArtifactDownloadContext, node: NodeDetail): PackageVersion? {
        with(context) {
            val artifactInfo = context.artifactInfo as OciArtifactInfo
            val packageKey = PackageKeys.ofName(repo.type, artifactInfo.packageName)
            val version = node.metadata[IMAGE_VERSION]?.toString() ?: return null
            return packageClient.findVersionByName(projectId, repoName, packageKey, version).data
        }
    }

    /**
     * 查询manifest文件内容
     */
    private fun queryManifest(context: ArtifactQueryContext): ArtifactInputStream? {
        val node = getNodeDetail(context.artifactInfo as OciArtifactInfo, context.artifactInfo.getArtifactFullPath())
        return storageManager.loadArtifactInputStream(node, context.storageCredentials)
    }

    /**
     * 查询仓库对应的所有image名
     */
    private fun queryCatalog(context: ArtifactQueryContext): CatalogResponse? {
        with(context.artifactInfo as OciTagArtifactInfo) {
            val n = context.getAttribute<Int>(N)
            val last = context.getAttribute<String>(LAST_TAG)
            val packageList = packageClient.listAllPackageNames(projectId, repoName).data.orEmpty()
            if (packageList.isEmpty()) return null
            val nameList = mutableListOf<String>().apply {
                packageList.forEach {
                    val packageName = OciUtils.getPackageNameFormPackageKey(
                        packageKey = it,
                        defaultType = artifactConfigurerSupport.getRepositoryType(),
                        extraTypes = artifactConfigurerSupport.getRepositoryTypes()
                    )
                    this.add(packageName)
                }
                this.sort()
            }
            val (imageList, left) = OciUtils.filterHandler(
                tags = nameList,
                n = n,
                last = last
            )
            return CatalogResponse(imageList, left)
        }
    }

    /**
     * 查询对应package下所有版本
     */
    private fun queryTagList(context: ArtifactQueryContext): TagsInfo? {
        with(context.artifactInfo as OciTagArtifactInfo) {
            val n = context.getAttribute<Int>(N)
            val last = context.getAttribute<String>(LAST_TAG)
            val packageKey = PackageKeys.ofName(context.repositoryDetail.type.name.toLowerCase(), packageName)
            val versionList = packageClient.listAllVersion(
                projectId,
                repoName,
                packageKey
            ).data.orEmpty()
            if (versionList.isEmpty()) return null
            val tagList = mutableListOf<String>().apply {
                versionList.forEach {
                    this.add(it.name)
                }
                this.sort()
            }
            val pair = OciUtils.filterHandler(
                tags = tagList,
                n = n,
                last = last
            )
            return TagsInfo(packageName, pair.first as List<String>, pair.second)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciRegistryLocalRepository::class.java)
    }
}
