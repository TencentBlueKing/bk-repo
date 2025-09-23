/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.proxy.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.proxy.ProxyFeignClientFactory
import com.tencent.bkrepo.generic.api.ProxyTemporaryAccessClient
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.GENERIC_MAPPING_URI
import com.tencent.bkrepo.proxy.service.DownloadService
import com.tencent.bkrepo.proxy.service.UploadService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/generic")
class GenericController(
    private val uploadService: UploadService,
    private val downloadService: DownloadService,
    private val proxyPermissionManager: ProxyPermissionManager
) {

    private val temporaryAccessClient: ProxyTemporaryAccessClient by lazy {
        ProxyFeignClientFactory.create("generic")
    }

    @GetMapping(GENERIC_MAPPING_URI)
    fun download(@ArtifactPathVariable artifactInfo: GenericArtifactInfo) {
        with(artifactInfo) {
            proxyPermissionManager.checkNodePermission(
                PermissionAction.READ,
                projectId,
                repoName,
                getArtifactFullPath()
            )
            downloadService.download(this)
        }
    }

    @PutMapping(GENERIC_MAPPING_URI)
    fun upload(@ArtifactPathVariable artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        with(artifactInfo) {
            proxyPermissionManager.checkRepoPermission(
                PermissionAction.WRITE,
                projectId,
                repoName
            )
        }
        uploadService.upload(artifactInfo, file)
    }

    @GetMapping("/temporary/download/$GENERIC_MAPPING_URI")
    fun temporaryDownload(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam token: String
    ) {
        with(artifactInfo) {
            val tokenInfo = temporaryAccessClient.checkToken(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                token = token,
                tokenType = TokenType.DOWNLOAD
            ).data!!
            downloadService.download(this)
            temporaryAccessClient.decrementPermitsToken(tokenInfo)
        }
    }

    @PutMapping("/temporary/upload/$GENERIC_MAPPING_URI")
    fun temporaryUpload(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        file: ArtifactFile,
        @RequestParam token: String
    ) {
        with(artifactInfo) {
            val tokenInfo = temporaryAccessClient.checkToken(
                projectId = projectId,
                repoName = repoName,
                fullPath = getArtifactFullPath(),
                token = token,
                tokenType = TokenType.UPLOAD
            ).data!!
            uploadService.upload(artifactInfo, file)
            temporaryAccessClient.decrementPermitsToken(tokenInfo)
        }
    }
}
