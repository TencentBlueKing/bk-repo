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

package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.common.artifact.exception.VersionNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.artifact.repository.HelmLocalRepository
import com.tencent.bkrepo.helm.pojo.artifact.HelmDeleteArtifactInfo
import com.tencent.bkrepo.helm.utils.HelmMetadataUtils
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.helm.utils.ObjectBuilderUtil
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class HelmOperationService : AbstractChartService() {

    /**
     * 删除chart或者prov
     */
    fun removeChartOrProv(context: ArtifactRemoveContext) {
        with(context.artifactInfo as HelmDeleteArtifactInfo) {
            if (version.isNotBlank()) {
                packageClient.findVersionByName(projectId, repoName, packageName, version).data?.let {
                    removeVersion(this, it, context.userId)
                } ?: throw VersionNotFoundException(version)
            } else {
                packageClient.listAllVersion(projectId, repoName, packageName).data.orEmpty().forEach {
                    removeVersion(this, it, context.userId)
                }
            }
            updatePackageExtension(context)
        }
    }

    /**
     * 节点删除后，将package extension信息更新
     */
    private fun updatePackageExtension(context: ArtifactRemoveContext) {
        with(context.artifactInfo as HelmDeleteArtifactInfo) {
            val version = packageClient.findPackageByKey(projectId, repoName, packageName).data?.latest
            try {
                val chartPath = HelmUtils.getChartFileFullPath(getArtifactName(), version!!)
                val map = nodeClient.getNodeDetail(projectId, repoName, chartPath).data?.metadata
                val chartInfo = map?.let { it1 -> HelmMetadataUtils.convertToObject(it1) }
                chartInfo?.appVersion?.let {
                    val packageUpdateRequest = ObjectBuilderUtil.buildPackageUpdateRequest(
                        context.artifactInfo,
                        PackageKeys.resolveHelm(packageName),
                        chartInfo.appVersion!!,
                        chartInfo.description
                    )
                    packageClient.updatePackage(packageUpdateRequest)
                }
            } catch (e: Exception) {
                HelmLocalRepository.logger.warn("can not convert meta data")
            }
        }
    }

    /**
     * 删除[version] 对应的node节点也会一起删除
     */
    private fun removeVersion(artifactInfo: HelmDeleteArtifactInfo, version: PackageVersion, userId: String) {
        with(artifactInfo) {
            packageClient.deleteVersion(projectId, repoName, packageName, version.name)
            val chartPath = HelmUtils.getChartFileFullPath(getArtifactName(), version.name)
            val provPath = HelmUtils.getProvFileFullPath(getArtifactName(), version.name)
            if (chartPath.isNotBlank()) {
                val request = NodeDeleteRequest(projectId, repoName, chartPath, userId)
                nodeClient.deleteNode(request)
                // 节点删除后，将package信息更新
            }
            if (provPath.isNotBlank()) {
                nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, provPath, userId))
            }
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmOperationService::class.java)
    }
}

