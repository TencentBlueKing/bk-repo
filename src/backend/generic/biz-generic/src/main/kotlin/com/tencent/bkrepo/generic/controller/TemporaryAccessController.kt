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

package com.tencent.bkrepo.generic.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.router.Router
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.CHUNKED_UPLOAD_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.DELTA_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.GENERIC_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericChunkedArtifactInfo
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.constant.CHUNKED_UPLOAD_CLIENT
import com.tencent.bkrepo.generic.constant.HEADER_OLD_FILE_PATH
import com.tencent.bkrepo.generic.pojo.ChunkedMetrics
import com.tencent.bkrepo.generic.pojo.TemporaryAccessToken
import com.tencent.bkrepo.generic.pojo.TemporaryAccessUrl
import com.tencent.bkrepo.generic.pojo.TemporaryUrlCreateRequest
import com.tencent.bkrepo.generic.service.TemporaryAccessService
import org.springframework.http.HttpMethod
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/temporary/")
class TemporaryAccessController(
    private val temporaryAccessService: TemporaryAccessService,
    private val permissionManager: PermissionManager,
    private val genericProperties: GenericProperties,
    ) {

    @PostMapping("/token/create")
    @Principal(PrincipalType.GENERAL)
    fun createToken(@RequestBody request: TemporaryTokenCreateRequest): Response<List<TemporaryAccessToken>> {
        return ResponseBuilder.success(temporaryAccessService.createToken(request))
    }

    @PostMapping("/url/create")
    fun createUrl(@RequestBody request: TemporaryUrlCreateRequest): Response<List<TemporaryAccessUrl>> {
        with(request) {
            fullPathSet.forEach {
                permissionManager.checkNodePermission(PermissionAction.WRITE, projectId, repoName, it)
            }
            return ResponseBuilder.success(temporaryAccessService.createUrl(request))
        }
    }

    @Router
    @GetMapping("/download/$GENERIC_MAPPING_URI")
    fun downloadByToken(
        artifactInfo: GenericArtifactInfo,
        @RequestParam token: String
    ) {
        val tokenInfo = temporaryAccessService.validateToken(token, artifactInfo, TokenType.DOWNLOAD)
        temporaryAccessService.download(artifactInfo)
        temporaryAccessService.decrementPermits(tokenInfo)
    }

    @CrossOrigin
    @PutMapping("/upload/$GENERIC_MAPPING_URI")
    fun uploadByToken(
        artifactInfo: GenericArtifactInfo,
        file: ArtifactFile,
        @RequestParam token: String
    ) {
        val tokenInfo = temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        temporaryAccessService.upload(artifactInfo, file)
        temporaryAccessService.decrementPermits(tokenInfo)
    }

    /**
     * 下载sign file
     * */
    @GetMapping("/sign/$DELTA_MAPPING_URI")
    fun downloadSignFile(
        artifactInfo: GenericArtifactInfo,
        @RequestParam token: String,
        @RequestParam md5: String? = null
    ) {
        val tokenInfo = temporaryAccessService.validateToken(token, artifactInfo, TokenType.DOWNLOAD)
        temporaryAccessService.sign(artifactInfo, md5)
        temporaryAccessService.decrementPermits(tokenInfo)
    }

    /**
     * 上传sign file
     * */
    @PutMapping("/sign/$DELTA_MAPPING_URI")
    fun uploadSignFile(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam token: String,
        @RequestParam md5: String,
        signFile: ArtifactFile
    ) {
        val tokenInfo = temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        temporaryAccessService.uploadSignFile(signFile, artifactInfo, md5)
        temporaryAccessService.decrementPermits(tokenInfo)
    }

    /**
     * 增量上传patch
     * */
    @PatchMapping("/patch/$DELTA_MAPPING_URI")
    fun patch(
        artifactInfo: GenericArtifactInfo,
        @RequestHeader(HEADER_OLD_FILE_PATH) oldFilePath: String,
        @RequestParam token: String,
        deltaFile: ArtifactFile
    ): SseEmitter {
        val tokenInfo = temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        val emitter = temporaryAccessService.patch(artifactInfo, oldFilePath, deltaFile)
        temporaryAccessService.decrementPermits(tokenInfo)
        return emitter
    }

    /**
     * chunked上传blob文件
     * chunked下载分为3步：
     * 1 Obtain a session ID (upload URL) (POST)
     * 2 Upload the chunks (PATCH)
     * 3 Close the session (PUT)
     */
    @PostMapping(CHUNKED_UPLOAD_MAPPING_URI)
    fun getUuidForChunkedUpload(
        artifactInfo: GenericChunkedArtifactInfo,
        artifactFile: ArtifactFile,
        @RequestParam token: String
    ) {
        if (!validateClientAgent())
            throw BadRequestException(CommonMessageCode.REQUEST_CONTENT_INVALID)
        temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        temporaryAccessService.getUuidForChunkedUpload(artifactInfo, artifactFile)
    }

    @RequestMapping(method = [RequestMethod.PATCH, RequestMethod.PUT], value = [CHUNKED_UPLOAD_MAPPING_URI])
    fun chunkedUpload(
        artifactInfo: GenericChunkedArtifactInfo,
        artifactFile: ArtifactFile,
        @RequestParam token: String
    ) {
        if (!validateClientAgent())
            throw BadRequestException(CommonMessageCode.REQUEST_CONTENT_INVALID)
        val tokenInfo = temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        temporaryAccessService.uploadArtifact(artifactInfo, artifactFile)

        // TODO 如果PUT请求没有发起，token如何让其失效
        if (HttpContextHolder.getRequest().method == HttpMethod.PUT.name) {
            temporaryAccessService.decrementPermits(tokenInfo)
        }
    }

    @PostMapping("/chunked/metrics")
    @Principal(PrincipalType.GENERAL)
    fun recordChunkedMetrics(
        @RequestBody metrics: ChunkedMetrics,
    ): Response<Void> {
        if (!validateClientAgent())
            throw BadRequestException(CommonMessageCode.REQUEST_CONTENT_INVALID)
        temporaryAccessService.reportChunkedMetrics(metrics)
        return ResponseBuilder.success()
    }

    @GetMapping("/cache/exist/check/$GENERIC_MAPPING_URI")
    fun cacheExistCheck(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam token: String,
    ): Response<Boolean> {
        temporaryAccessService.validateToken(token, artifactInfo, TokenType.DOWNLOAD)
        return ResponseBuilder.success(temporaryAccessService.cacheExistCheck(artifactInfo))
    }


    /**
     * 判断来源是否可信
     */
    private fun validateClientAgent(): Boolean {
        val uploadClient = HttpContextHolder.getRequest().getHeader(CHUNKED_UPLOAD_CLIENT)
        return !uploadClient.isNullOrEmpty() && genericProperties.chunkedUploadClients.contains(uploadClient)
    }
}
