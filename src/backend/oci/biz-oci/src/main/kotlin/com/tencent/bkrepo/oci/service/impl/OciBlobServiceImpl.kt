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

package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.oci.constant.BLOB_PATH_VERSION_KEY
import com.tencent.bkrepo.oci.constant.BLOB_PATH_VERSION_VALUE
import com.tencent.bkrepo.oci.constant.OciMessageCode
import com.tencent.bkrepo.oci.constant.REPO_TYPE
import com.tencent.bkrepo.oci.exception.OciBadRequestException
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.pojo.response.ResponseProperty
import com.tencent.bkrepo.oci.service.OciBlobService
import com.tencent.bkrepo.oci.service.OciOperationService
import com.tencent.bkrepo.oci.util.ObjectBuildUtils
import com.tencent.bkrepo.oci.util.OciLocationUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OciBlobServiceImpl(
    private val storage: StorageService,
    private val repoClient: RepositoryClient,
    private val nodeClient: NodeClient,
    private val ociOperationService: OciOperationService,
    private val permissionManager: PermissionManager
) : OciBlobService {

    override fun startUploadBlob(artifactInfo: OciBlobArtifactInfo, artifactFile: ArtifactFile) {
        with(artifactInfo) {
            logger.info("Handling bolb upload request $artifactInfo in ${getRepoIdentify()} .")
            if (digest.isNullOrBlank()) {
                logger.info("Will use post then put to upload blob...")
                obtainSessionIdForUpload(artifactInfo)
            } else {
                logger.info("Will use single post to upload blob...")
                singlePostUpload(artifactFile)
            }
        }
    }

    /**
     * 使用单个post请求直接上传文件
     */
    private fun singlePostUpload(artifactFile: ArtifactFile) {
        val context = ArtifactUploadContext(artifactFile)
        ArtifactContextHolder.getRepository().upload(context)
    }

    /**
     * 获取上传文件uuid
     */
    private fun obtainSessionIdForUpload(artifactInfo: OciBlobArtifactInfo) {
        with(artifactInfo) {
            if (mount.isNullOrBlank()) {
                logger.info("Will obtain uuid for uploading blobs in repo ${artifactInfo.getRepoIdentify()}.")
                val uuidCreated = startAppend(this)
                val domain = ociOperationService.getReturnDomain(HttpContextHolder.getRequest())
                val responseProperty = ResponseProperty(
                    uuid = uuidCreated,
                    location = OciLocationUtils.blobUUIDLocation(uuidCreated, artifactInfo),
                    status = HttpStatus.ACCEPTED,
                    contentLength = 0
                )
                OciResponseUtils.buildUploadResponse(
                    domain,
                    responseProperty,
                    HttpContextHolder.getResponse()
                )
            } else {
                mountBlob(artifactInfo)
            }
        }
    }

    private fun mountBlob(artifactInfo: OciBlobArtifactInfo) {
        with(artifactInfo) {
            val domain = ociOperationService.getReturnDomain(HttpContextHolder.getRequest())
            val ociDigest = OciDigest(mount)
            val (mountProjectId, mountRepoName) = splitRepoInfo(from) ?: Pair(projectId, repoName)
            if (mountProjectId != projectId && mountRepoName != repoName) {
                try {
                    permissionManager.checkRepoPermission(
                        action = PermissionAction.READ,
                        projectId = mountProjectId,
                        repoName = mountRepoName
                    )
                } catch (e: ErrorCodeException) {
                    buildSessionIdLocationForUpload(this, domain)
                    return
                }
            }
            val nodeProperty = ociOperationService.getNodeByDigest(
                mountProjectId, mountRepoName, ociDigest.toString()
            ) ?: run {
                logger.warn("Could not find $ociDigest in repo $mountProjectId|$mountRepoName to mount")
                buildSessionIdLocationForUpload(this, domain)
                return
            }
            // 用于新版本 blobs 路径区分
            val metadata: MutableList<MetadataModel> = mutableListOf(
                MetadataModel(key = BLOB_PATH_VERSION_KEY, value = BLOB_PATH_VERSION_VALUE, system = true)
            )
            val nodeCreateRequest = ObjectBuildUtils.buildNodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                size = nodeProperty.size!!,
                sha256 = ociDigest.hex,
                fullPath = OciLocationUtils.buildDigestBlobsPath(packageName, ociDigest),
                md5 = nodeProperty.md5!!,
                metadata = metadata
            )
            nodeClient.createNode(nodeCreateRequest)
            val blobLocation = OciLocationUtils.blobLocation(ociDigest, this)
            val responseProperty = ResponseProperty(
                location = blobLocation,
                status =  HttpStatus.CREATED
            )
            OciResponseUtils.buildUploadResponse(
                domain = domain,
                responseProperty = responseProperty,
                response = HttpContextHolder.getResponse()
            )
        }
    }

    private fun buildSessionIdLocationForUpload(artifactInfo: OciBlobArtifactInfo, domain: String) {
        val uuidCreated = startAppend(artifactInfo)
        val responseProperty = ResponseProperty(
            uuid = uuidCreated,
            location = OciLocationUtils.blobUUIDLocation(uuidCreated, artifactInfo),
            status =  HttpStatus.ACCEPTED,
            contentLength = 0
        )
        OciResponseUtils.buildUploadResponse(
            domain = domain,
            responseProperty = responseProperty,
            response = HttpContextHolder.getResponse()
        )
    }

    private fun splitRepoInfo(from: String?): Pair<String, String>? {
        if (from.isNullOrEmpty()) return null
        val values = from.split(CharPool.SLASH)
        return Pair(values[0], values[1])
    }

    /**
     * start a append upload
     * @return String append Id
     */
    fun startAppend(artifactInfo: OciBlobArtifactInfo): String {
        with(artifactInfo) {
            // check repository
            val result = repoClient.getRepoDetail(projectId, repoName, REPO_TYPE).data ?: run {
                ArtifactContextHolder.queryRepoDetailFormExtraRepoType(projectId, repoName)
            }
            logger.debug("Start to append file in ${getRepoIdentify()}")
            return storage.createAppendId(result.storageCredentials)
        }
    }

    override fun uploadBlob(artifactInfo: OciBlobArtifactInfo, artifactFile: ArtifactFile) {
        logger.info("handing request upload blob [$artifactInfo] in repo ${artifactInfo.getRepoIdentify()}.")
        val context = ArtifactUploadContext(artifactFile)
        // 3种上传方式都在local里面做处理
        ArtifactContextHolder.getRepository().upload(context)
    }

    override fun downloadBlob(artifactInfo: OciBlobArtifactInfo) {
        with(artifactInfo) {
            logger.info(
                "Handling blob download request for blob [${getDigest()}] in repo [${artifactInfo.getRepoIdentify()}]"
            )
            val context = ArtifactDownloadContext()
            ArtifactContextHolder.getRepository().download(context)
        }
    }

    override fun deleteBlob(artifactInfo: OciBlobArtifactInfo) {
        logger.info(
            "Handling delete blob request for package [${artifactInfo.packageName}] " +
                "with digest [${artifactInfo.digest}] in repo [${artifactInfo.getRepoIdentify()}]"
        )
        if (artifactInfo.digest.isNullOrBlank())
            throw OciBadRequestException(OciMessageCode.OCI_DELETE_RULES, artifactInfo.getArtifactFullPath())
        val context = ArtifactRemoveContext()
        ArtifactContextHolder.getRepository().remove(context)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciBlobServiceImpl::class.java)
    }
}
