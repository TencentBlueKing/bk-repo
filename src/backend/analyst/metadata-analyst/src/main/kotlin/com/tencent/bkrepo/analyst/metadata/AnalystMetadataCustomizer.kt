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

package com.tencent.bkrepo.analyst.metadata

import com.tencent.bkrepo.analyst.api.ScanQualityClient
import com.tencent.bkrepo.analyst.config.AnalystProperties
import com.tencent.bkrepo.common.artifact.pojo.RepositoryId
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.metadata.listener.MetadataCustomizer
import com.tencent.bkrepo.common.metadata.util.MetadataUtils
import com.tencent.bkrepo.common.metadata.util.MetadataUtils.generateForbidMetadata
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.metadata.FORBID_REASON_NOT_SCANNED
import com.tencent.bkrepo.repository.pojo.metadata.ForbidType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionUpdateRequest
import org.slf4j.LoggerFactory

class AnalystMetadataCustomizer(
    private val analystProperties: AnalystProperties,
    private val scanQualityClient: ScanQualityClient,
) : MetadataCustomizer() {
    override fun customize(req: NodeCreateRequest, extra: List<MetadataModel>?): List<MetadataModel> {
        with(req) {
            val metadata = merge(MetadataUtils.compatibleConvertToModel(metadata, nodeMetadata), extra)
            val repoType = getRepoType(projectId, repoName) ?: return metadata
            if (repoType == RepositoryType.GENERIC) {
                resolveForbidMetadata(projectId, repoName, repoType.name, fullPath)?.let { metadata.addAll(it) }
            }
            return metadata
        }
    }

    override fun customize(req: PackageVersionCreateRequest, extra: List<MetadataModel>?): List<MetadataModel> {
        with(req) {
            val metadata = merge(MetadataUtils.compatibleConvertToModel(metadata, packageMetadata), extra)
            val repoType = getRepoType(projectId, repoName) ?: return metadata
            resolveForbidMetadata(
                projectId, repoName, repoType.name, packageName = packageName, packageVersion = versionName
            )?.let { metadata.addAll(it) }
            return metadata
        }
    }

    override fun customize(
        req: PackageVersionUpdateRequest,
        oldMetadataModel: List<MetadataModel>,
        extra: List<MetadataModel>?
    ): List<MetadataModel> {
        with(req) {
            return if (req.metadata == null && req.packageMetadata == null) {
                oldMetadataModel
            } else {
                val metadata = merge(MetadataUtils.compatibleConvertToModel(req.metadata, req.packageMetadata), extra)
                val repoType = getRepoType(projectId, repoName) ?: return metadata
                val packageName = PackageKeys.resolve(repoType, packageKey)
                resolveForbidMetadata(
                    projectId, repoName, repoType.name, packageName = packageName, packageVersion = versionName
                )?.let { metadata.addAll(it) }
                metadata
            }
        }
    }

    /**
     * 判断制品是否需要禁用，需要禁用时返回制品禁用元数据，用于添加到制品元数据中
     */
    private fun resolveForbidMetadata(
        projectId: String,
        repoName: String,
        repoType: String,
        fullPath: String? = null,
        packageName: String? = null,
        packageVersion: String? = null,
    ): List<MetadataModel>? {
        if (!analystProperties.enableForbidNotScanned) {
            return null
        }

        require(fullPath != null || (packageName != null && packageVersion != null)) {}
        try {
            val shouldForbid = scanQualityClient.shouldForbidBeforeScanned(
                projectId, repoName, repoType, fullPath, packageName, packageVersion
            ).data == true
            if (shouldForbid) {
                return generateForbidMetadata(true, FORBID_REASON_NOT_SCANNED, ForbidType.NOT_SCANNED, SYSTEM_USER)
            }
        } catch (e: Exception) {
            logger.error("Pre-check forbid status for [$projectId/$repoName$fullPath] failed", e)
        }
        return emptyList()
    }

    private fun getRepoType(projectId: String, repoName: String): RepositoryType? {
        try {
            return ArtifactContextHolder.getRepoDetail(RepositoryId(projectId, repoName)).type
        } catch (e: Exception) {
            logger.error("get [$projectId/$repoName] repo type failed", e)
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AnalystMetadataCustomizer::class.java)
    }
}
