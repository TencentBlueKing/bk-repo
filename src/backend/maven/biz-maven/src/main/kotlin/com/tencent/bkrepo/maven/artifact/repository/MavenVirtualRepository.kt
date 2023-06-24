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

package com.tencent.bkrepo.maven.artifact.repository

import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.constants.SNAPSHOT_TIMESTAMP
import com.tencent.bkrepo.maven.constants.X_CHECKSUM_SHA1
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotMetadataChecksumUri
import com.tencent.bkrepo.maven.util.MavenStringUtils.isSnapshotMetadataUri
import org.springframework.stereotype.Component

@Component
class MavenVirtualRepository : VirtualRepository() {

    override fun query(context: ArtifactQueryContext): Any? {
        throw MethodNotAllowedException()
    }

    @Suppress("UNCHECKED_CAST", "ReturnCount")
    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val fullPath = context.artifactInfo.getArtifactFullPath()
        // 从成员仓库中取最新时间戳的snapshot版本元数据文件或元数据摘要文件
        if (fullPath.isSnapshotMetadataUri() || fullPath.isSnapshotMetadataChecksumUri()) {
            val resources = mapEachLocalAndFirstRemote(context) { sub, repository ->
                require(sub is ArtifactDownloadContext)
                require(repository is AbstractArtifactRepository)
                val resource = repository.onDownload(sub)
                val timestamp = sub.getAndRemoveAttribute<String>(SNAPSHOT_TIMESTAMP)
                val sha1 = with(context.response) {
                    getHeader(X_CHECKSUM_SHA1)?.also { setHeader(X_CHECKSUM_SHA1, null) }
                }
                if (!timestamp.isNullOrBlank() && resource != null) {
                    Triple(timestamp, resource, sha1)
                } else null
            } as List<Triple<String, ArtifactResource, String?>>
            val latestResource = resources.maxByOrNull { it.first }
            resources.filterNot { it == latestResource }.forEach { it.second.getSingleStream().close() }
            return latestResource?.run {
                third?.let { context.response.setHeader(X_CHECKSUM_SHA1, it) }
                second
            }
        }
        // 上传版本时请求包级别元数据及其摘要, 从默认部署仓库返回
        if ((context.artifactInfo as MavenArtifactInfo).isAboutPackageMetadata()) {
            try {
                mapDeploymentRepo(context) { sub, repository ->
                    require(sub is ArtifactDownloadContext)
                    require(repository is AbstractArtifactRepository)
                    repository.onDownload(sub)
                }?.let { return it }
            } catch (ignore: Exception) {}
        }
        return super.onDownload(context)
    }
}
