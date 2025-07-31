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

package com.tencent.bkrepo.preview.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.service.impl.CompressFilePreviewImpl
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/compressed/report")
class CompressedReportPreviewController(
    private val compressFilePreviewImpl: CompressFilePreviewImpl,
    private val config: PreviewConfig
) {

    @RequestMapping("/preview/{projectId}/{repoName}/**")
    @Permission(type = ResourceType.NODE, action = PermissionAction.DOWNLOAD)
    fun preview(
        @ArtifactPathVariable artifactInfo: PreviewArtifactInfo
    ) {
        val reportFolderPath = artifactInfo.getArtifactFullPath()
            .split(StringPool.SLASH).subList(0,4)
            .joinToString(StringPool.SLASH)
        val artifactUri = "$reportFolderPath/${config.compressedReportFilename}"
        val zipEntryPath = artifactInfo.getArtifactFullPath()
            .removePrefix(reportFolderPath)
            .removePrefix(StringPool.SLASH)
        val fileAttribute = FileAttribute(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            artifactUri = artifactUri,
            zipEntryPath = zipEntryPath
        )
        compressFilePreviewImpl.filePreviewHandle(fileAttribute)
    }
}