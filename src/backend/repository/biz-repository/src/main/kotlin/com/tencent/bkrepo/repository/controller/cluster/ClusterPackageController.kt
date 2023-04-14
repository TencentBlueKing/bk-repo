/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.cluster

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.cluster.ClusterPackageClient
import com.tencent.bkrepo.repository.pojo.packages.request.PackageUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionUpdateRequest
import com.tencent.bkrepo.repository.service.packages.PackageService
import org.springframework.web.bind.annotation.RestController

@RestController
class ClusterPackageController(
    private val packageService: PackageService,
    private val permissionManager: PermissionManager
) : ClusterPackageClient {

    override fun createVersion(request: PackageVersionCreateRequest, realIpAddress: String?): Response<Void> {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, request.projectId, request.repoName)
        packageService.createPackageVersion(request, realIpAddress)
        return ResponseBuilder.success()
    }

    override fun deletePackage(
        projectId: String,
        repoName: String,
        packageKey: String,
        realIpAddress: String?
    ): Response<Void> {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
        packageService.deletePackage(projectId, repoName, packageKey, realIpAddress)
        return ResponseBuilder.success()
    }

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        realIpAddress: String?
    ): Response<Void> {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
        packageService.deleteVersion(projectId, repoName, packageKey, version, realIpAddress)
        return ResponseBuilder.success()
    }

    override fun updatePackage(request: PackageUpdateRequest, realIpAddress: String?): Response<Void> {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, request.projectId, request.repoName)
        packageService.updatePackage(request, realIpAddress)
        return ResponseBuilder.success()
    }

    override fun updateVersion(request: PackageVersionUpdateRequest, realIpAddress: String?): Response<Void> {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, request.projectId, request.repoName)
        packageService.updateVersion(request, realIpAddress)
        return ResponseBuilder.success()
    }
}
