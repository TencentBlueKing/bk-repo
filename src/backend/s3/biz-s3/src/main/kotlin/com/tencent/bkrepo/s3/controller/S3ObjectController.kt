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

package com.tencent.bkrepo.s3.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.s3.artifact.S3ArtifactInfo
import com.tencent.bkrepo.s3.artifact.S3ArtifactInfo.Companion.GENERIC_MAPPING_URI
import com.tencent.bkrepo.s3.constant.S3HttpHeaders.X_AMZ_COPY_SOURCE
import com.tencent.bkrepo.s3.pojo.CopyObjectResult
import com.tencent.bkrepo.s3.pojo.ListBucketResult
import com.tencent.bkrepo.s3.service.S3ObjectService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class S3ObjectController(
    private val s3ObjectService: S3ObjectService,
) {
    @GetMapping(GENERIC_MAPPING_URI)
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    fun getOrListObject(
        @ArtifactPathVariable artifactInfo: S3ArtifactInfo,
        @RequestParam delimiter: String?,
        @RequestParam("max-keys") maxKeys: Int?,
        @RequestParam prefix: String?,
    ): ListBucketResult? {
        val queryParamsNotNull = delimiter != null || maxKeys != null || prefix != null
        if (queryParamsNotNull || artifactInfo.getArtifactFullPath() == StringPool.ROOT) {
            return s3ObjectService.listObjects(
                artifactInfo = artifactInfo,
                maxKeys = maxKeys ?: 1000,
                delimiter = delimiter ?: StringPool.SLASH,
                prefix = prefix ?: ""
            )
        } else {
            s3ObjectService.getObject(artifactInfo)
            return null
        }
    }

    @PutMapping(GENERIC_MAPPING_URI)
    @Permission(type = ResourceType.NODE, action = PermissionAction.WRITE)
    fun putOrCopyObject(@ArtifactPathVariable artifactInfo: S3ArtifactInfo, file: ArtifactFile): CopyObjectResult? {
        // 根目录不需要创建
        if (artifactInfo.getArtifactFullPath() == StringPool.ROOT) {
            return null
        }
        return if (HeaderUtils.getHeader(X_AMZ_COPY_SOURCE).isNullOrEmpty()) {
            s3ObjectService.putObject(artifactInfo, file)
            null
        } else {
            s3ObjectService.copyObject(artifactInfo)
        }

    }
}
