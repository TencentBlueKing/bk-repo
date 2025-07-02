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

package com.tencent.bkrepo.common.metadata.service.packages.impl.edge

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeEdgePackageCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.repository.api.cluster.ClusterPackageClient
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.listener.MetadataCustomizer
import com.tencent.bkrepo.repository.pojo.packages.request.PackageUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionUpdateRequest
import com.tencent.bkrepo.common.metadata.search.packages.PackageSearchInterpreter
import com.tencent.bkrepo.common.metadata.service.packages.impl.PackageServiceImpl
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service


@Service
@Conditional(SyncCondition::class, CommitEdgeEdgePackageCondition::class)
@Primary
class EdgePackageServiceImpl(
    repositoryDao: RepositoryDao,
    packageDao: PackageDao,
    packageVersionDao: PackageVersionDao,
    packageSearchInterpreter: PackageSearchInterpreter,
    clusterProperties: ClusterProperties,
    metadataCustomizer: MetadataCustomizer?,
) : PackageServiceImpl(
    repositoryDao,
    packageDao,
    packageVersionDao,
    packageSearchInterpreter,
    metadataCustomizer,
) {

    private val centerPackageClient: ClusterPackageClient by lazy {
        FeignClientFactory.create(clusterProperties.center, "repository", clusterProperties.self.name)
    }

    override fun createPackageVersion(request: PackageVersionCreateRequest, realIpAddress: String?) {
        centerPackageClient.createVersion(request)
        super.createPackageVersion(request, realIpAddress)
    }

    override fun updateVersion(request: PackageVersionUpdateRequest, realIpAddress: String?) {
        centerPackageClient.updateVersion(request)
        super.updateVersion(request, realIpAddress)
    }

    override fun updatePackage(request: PackageUpdateRequest, realIpAddress: String?) {
        centerPackageClient.updatePackage(request)
        super.updatePackage(request, realIpAddress)
    }

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
        realIpAddress: String?,
        contentPath: String?,
    ) {
        centerPackageClient.deleteVersion(projectId, repoName, packageKey, versionName, realIpAddress, contentPath)
        super.deleteVersion(projectId, repoName, packageKey, versionName, realIpAddress, contentPath)
    }

    override fun deletePackage(projectId: String, repoName: String, packageKey: String, realIpAddress: String?) {
        centerPackageClient.deletePackage(projectId, repoName, packageKey, realIpAddress)
        super.deletePackage(projectId, repoName, packageKey, realIpAddress)
    }
}
