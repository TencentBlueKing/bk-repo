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

package com.tencent.bkrepo.s3.service

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.api.constant.ensurePrefix
import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.generic.configuration.AutoIndexRepositorySettings
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.s3.artifact.S3ArtifactInfo
import com.tencent.bkrepo.s3.constant.NO_SUCH_ACCESS
import com.tencent.bkrepo.s3.constant.NO_SUCH_KEY
import com.tencent.bkrepo.s3.constant.S3HttpHeaders.X_AMZ_COPY_SOURCE
import com.tencent.bkrepo.s3.constant.S3HttpHeaders.X_AMZ_METADATA_DIRECTIVE
import com.tencent.bkrepo.s3.constant.S3HttpHeaders.X_AMZ_META_PREFIX
import com.tencent.bkrepo.s3.constant.S3MessageCode
import com.tencent.bkrepo.s3.exception.S3AccessDeniedException
import com.tencent.bkrepo.s3.exception.S3NotFoundException
import com.tencent.bkrepo.s3.pojo.CopyObjectResult
import com.tencent.bkrepo.s3.pojo.ListBucketResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URLDecoder

/**
 * S3对象服务类
 */
@Service
class S3ObjectService(
    private val nodeClient: NodeClient,
    private val metadataClient: MetadataClient
) : ArtifactService() {

    fun getObject(artifactInfo: S3ArtifactInfo) {
        val node = ArtifactContextHolder.getNodeDetail(artifactInfo) ?:
            throw S3NotFoundException(
                HttpStatus.NOT_FOUND,
                S3MessageCode.S3_NO_SUCH_KEY,
                params = arrayOf(NO_SUCH_KEY, artifactInfo.getArtifactFullPath())
            )
        ArtifactContextHolder.getRepoDetail()
        val context = ArtifactDownloadContext()
        //仓库未开启自动创建目录索引时不允许访问目录
        val autoIndexSettings = AutoIndexRepositorySettings.from(context.repositoryDetail.configuration)
        if (node.folder && autoIndexSettings?.enabled == false) {
            logger.warn("${artifactInfo.getArtifactFullPath()} is folder " +
                    "or repository is not enabled for automatic directory index creation")
            throw S3AccessDeniedException(
                HttpStatus.FORBIDDEN,
                S3MessageCode.S3_NO_SUCH_ACCESS,
                params = arrayOf(NO_SUCH_ACCESS, artifactInfo.getArtifactFullPath())
            )
        }
        repository.download(context)
    }

    fun putObject(artifactInfo: S3ArtifactInfo, file: ArtifactFile) {
        val context = ArtifactUploadContext(file)
        repository.upload(context)
    }

    fun listObjects(artifactInfo: S3ArtifactInfo, maxKeys: Int, delimiter: String, prefix: String): ListBucketResult {
        val projectId = artifactInfo.projectId
        val repoName = artifactInfo.repoName
        val nodeQueryBuilder = NodeQueryBuilder()
            .projectId(projectId).repoName(repoName)
            .apply {
                if (prefix.isEmpty()) {
                    path(StringPool.ROOT)
                } else {
                    path(PathUtils.normalizePath(prefix.ensurePrefix(SLASH)))
                }
            }
        val folderQueryBuilder = nodeQueryBuilder.newBuilder().excludeFile().select(NodeDetail::fullPath.name)
        val folders = nodeClient.queryWithoutCount(folderQueryBuilder.build()).data!!.records
            .map {
                it[NodeDetail::fullPath.name].toString().removePrefix(SLASH).ensureSuffix(SLASH)
            }
        val fileQueryBuilder = nodeQueryBuilder.newBuilder().excludeFolder()
        val files = nodeClient.queryWithoutCount(fileQueryBuilder.build()).data!!.records
        return ListBucketResult(repoName, files, maxKeys, prefix, folders)
    }

    fun copyObject(artifactInfo: S3ArtifactInfo): CopyObjectResult {
        val source = HeaderUtils.getHeader(X_AMZ_COPY_SOURCE) ?: throw IllegalArgumentException(X_AMZ_COPY_SOURCE)
        val delimiterIndex = source.indexOf(SLASH)
        val srcRepoName = source.substring(0, delimiterIndex)
        val srcFullPath = URLDecoder.decode(source.substring(delimiterIndex), Charsets.UTF_8.name())
        val copyRequest = NodeMoveCopyRequest(
            srcProjectId = artifactInfo.projectId,
            srcRepoName = srcRepoName,
            srcFullPath = srcFullPath,
            destProjectId = artifactInfo.projectId,
            destRepoName = artifactInfo.repoName,
            destFullPath = artifactInfo.getArtifactFullPath(),
            overwrite = true,
            operator = SecurityUtils.getUserId()
        )
        var dstNode = nodeClient.copyNode(copyRequest).data!!
        dstNode = replaceMetadata(dstNode)
        return CopyObjectResult(
            eTag = "\"${dstNode.md5}\"",
            lastModified = dstNode.lastModifiedDate,
            checksumCRC32 = "",
            checksumCRC32C = "",
            checksumSHA1 = "",
            checksumSHA256 = "\"${dstNode.sha256}\""
        )
    }

    fun replaceMetadata(nodeDetail: NodeDetail): NodeDetail {
        val directive = HeaderUtils.getHeader(X_AMZ_METADATA_DIRECTIVE)
        if (directive.equals("REPLACE", true)) {
            val metadataHeader = HeaderUtils.getHeaderNames()?.filter {
                it.startsWith(X_AMZ_META_PREFIX, true)
            }.orEmpty()
            val saveRequest = MetadataSaveRequest(
                projectId = nodeDetail.projectId,
                repoName = nodeDetail.repoName,
                fullPath = nodeDetail.fullPath,
                nodeMetadata = metadataHeader.map { MetadataModel(it, HeaderUtils.getHeader(it).toString()) },
                replace = true
            )
            metadataClient.saveMetadata(saveRequest)
            return nodeDetail.copy(nodeMetadata = saveRequest.nodeMetadata!!)
        }
        return nodeDetail
    }

    companion object {
        private val logger = LoggerFactory.getLogger(S3ObjectService::class.java)
    }
}
