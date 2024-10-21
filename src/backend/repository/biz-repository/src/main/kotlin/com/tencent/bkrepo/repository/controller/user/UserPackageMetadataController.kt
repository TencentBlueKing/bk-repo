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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.UserPackageMetadataSaveRequest
import com.tencent.bkrepo.common.metadata.service.metadata.PackageMetadataService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 元数据接口实现类
 */
@Api("包元数据用户接口")
@RestController
@RequestMapping("/api/metadata/package")
class UserPackageMetadataController(
    private val packageMetadataService: PackageMetadataService
) {

    @ApiOperation("创建/更新禁用元数据列表")
    @Permission(type = ResourceType.REPO, action = PermissionAction.UPDATE)
    @PostMapping("/forbid/{projectId}/{repoName}")
    fun forbidMetadata(
        @RequestAttribute userId: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody metadataSaveRequest: UserPackageMetadataSaveRequest
    ): Response<Void> {
        val request = with(metadataSaveRequest) {
            PackageMetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                version = version,
                versionMetadata = metadataSaveRequest.versionMetadata
            )
        }
        packageMetadataService.addForbidMetadata(request)
        return ResponseBuilder.success()
    }

    @ApiOperation("保存元数据")
    @Permission(type = ResourceType.REPO, action = PermissionAction.UPDATE)
    @PostMapping("/{projectId}/{repoName}")
    fun saveMetadata(
        @RequestAttribute userId: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestBody metadataSaveRequest: UserPackageMetadataSaveRequest
    ): Response<Void> {
        val request = with(metadataSaveRequest) {
            PackageMetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                version = version,
                versionMetadata = metadataSaveRequest.versionMetadata?.map { it.copy(system = false) }
            )
        }
        packageMetadataService.saveMetadata(request)
        return ResponseBuilder.success()
    }
}
