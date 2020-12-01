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

package com.tencent.bkrepo.helm.async

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.helm.model.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class HelmPackageHandler(
    private val packageClient: PackageClient
) {

    /**
     * 创建包版本
     */
    @Async
    fun createVersion(
        userId: String,
        artifactInfo: ArtifactInfo,
        chartInfo: HelmChartMetadata,
        size: Long
    ) {
        val name = chartInfo.name
        val description = chartInfo.description
        val version = chartInfo.version
        val contentPath = HelmUtils.getChartFileFullPath(name, version)
        with(artifactInfo) {
            val packageVersionCreateRequest =
                PackageVersionCreateRequest(
                    projectId = projectId,
                    repoName = repoName,
                    packageName = name,
                    packageKey = PackageKeys.ofHelm(name),
                    packageType = PackageType.HELM,
                    packageDescription = description,
                    versionName = version,
                    size = size,
                    manifestPath = null,
                    artifactPath = contentPath,
                    stageTag = null,
                    metadata = null,
                    overwrite = false,
                    createdBy = userId
                )
            packageClient.createVersion(packageVersionCreateRequest).apply {
                logger.info("user: [$userId] create package version [$packageVersionCreateRequest] success!")
            }
        }
    }

    /**
     * 删除包
     */
    @Async
    fun deletePackage(userId: String, name: String, artifactInfo: ArtifactInfo) {
        val packageKey = PackageKeys.ofHelm(name)
        with(artifactInfo) {
            packageClient.deletePackage(projectId, repoName, packageKey).apply {
                logger.info("user: [$userId] delete package [$name] in repo [$projectId/$repoName] success!")
            }
        }
    }

    /**
     * 删除版本
     */
    @Async
    fun deleteVersion(userId: String, name: String, version: String, artifactInfo: ArtifactInfo) {
        val packageKey = PackageKeys.ofHelm(name)
        with(artifactInfo) {
            packageClient.deleteVersion(projectId, repoName, packageKey, version).apply {
                logger.info("user: [$userId] delete package [$name] with version [$version] in repo [$projectId/$repoName] success!")
            }
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmPackageHandler::class.java)
    }
}
