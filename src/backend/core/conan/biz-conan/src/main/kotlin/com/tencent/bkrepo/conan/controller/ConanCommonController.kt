/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.conan.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes.APPLICATION_JSON_WITHOUT_CHARSET
import com.tencent.bkrepo.common.api.constant.MediaTypes.TEXT_PLAIN
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.conan.constant.X_CONAN_SERVER_CAPABILITIES
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.AUTHENTICATE_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.AUTHENTICATE_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.CHECK_CREDENTIALS_V1
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.CHECK_CREDENTIALS_V2
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.PING_V1
import com.tencent.bkrepo.conan.service.ConanAuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * conan公共基础请求处理类（v1/v2公用）
 */
@RestController
class ConanCommonController(
    private val conanAuthService: ConanAuthService
) {
    @GetMapping(PING_V1)
    fun ping(
        @PathVariable projectId: String,
        @PathVariable repoName: String
    ): ResponseEntity<Any> {
        return buildResponse(StringPool.EMPTY, TEXT_PLAIN)
    }

    @GetMapping(CHECK_CREDENTIALS_V1, CHECK_CREDENTIALS_V2)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun checkCredentials(
        @PathVariable projectId: String,
        @PathVariable repoName: String
    ): ResponseEntity<Any> {
        val authorizationHeader = HttpContextHolder.getRequest().getHeader(HttpHeaders.AUTHORIZATION)
            ?: throw AuthenticationException("Logged user needed!")
        val jwtToken = conanAuthService.checkCredentials(authorizationHeader)
        return buildResponse(jwtToken, TEXT_PLAIN)
    }

    @GetMapping(AUTHENTICATE_V1, AUTHENTICATE_V2)
    fun authenticate(
        @PathVariable projectId: String,
        @PathVariable repoName: String
    ): ResponseEntity<Any> {
        val authorizationHeader = HttpContextHolder.getRequest().getHeader(HttpHeaders.AUTHORIZATION)
            ?: throw AuthenticationException("Wrong user or password")
        val jwtToken = conanAuthService.authenticate(authorizationHeader)
        return buildResponse(jwtToken, TEXT_PLAIN)
    }

    companion object {
        val capabilities = listOf("complex_search", "checksum_deploy", "revisions", "matrix_params")
        fun buildResponse(body: Any, contentType: String = APPLICATION_JSON_WITHOUT_CHARSET): ResponseEntity<Any> {
            return ResponseEntity.ok()
                .header(X_CONAN_SERVER_CAPABILITIES, capabilities.joinToString(","))
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(body)
        }
    }
}
