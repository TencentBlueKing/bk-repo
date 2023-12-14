/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.ddc.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.ddc.artifact.CompressedBlobArtifactInfo
import com.tencent.bkrepo.ddc.service.CompressedBlobService
import com.tencent.bkrepo.ddc.utils.MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/{projectId}/api/v1/compressed-blobs")
@RestController
class CompressedBlobController(
    private val compressedBlobService: CompressedBlobService
) {

    @ApiOperation("获取压缩后的缓存")
    @GetMapping(
        "/{repoName}/{$PATH_VARIABLE_CONTENT_ID}",
        produces = [MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER, MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    @Permission(ResourceType.REPO, action = PermissionAction.READ)
    fun get(
        @ApiParam(value = "ddc compressed blob", required = true)
        @ArtifactPathVariable
        artifactInfo: CompressedBlobArtifactInfo,
    ) {
        compressedBlobService.get(artifactInfo)
    }

    @ApiOperation("上传压缩后的缓存")
    @PutMapping("/{repoName}/{$PATH_VARIABLE_CONTENT_ID}", consumes = [MEDIA_TYPE_UNREAL_UNREAL_COMPRESSED_BUFFER])
    @Permission(ResourceType.REPO, action = PermissionAction.WRITE)
    fun put(
        @ApiParam(value = "ddc compressed blob", required = true)
        @ArtifactPathVariable
        artifactInfo: CompressedBlobArtifactInfo,
        artifactFile: ArtifactFile
    ) {
        compressedBlobService.put(artifactInfo, artifactFile)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CompressedBlobController::class.java)
        const val PATH_VARIABLE_CONTENT_ID = CompressedBlobArtifactInfo.PATH_VARIABLE_CONTENT_ID
    }
}
