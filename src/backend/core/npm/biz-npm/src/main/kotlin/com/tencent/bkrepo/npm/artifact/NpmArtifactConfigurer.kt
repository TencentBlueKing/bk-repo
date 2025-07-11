/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurerSupport
import com.tencent.bkrepo.common.artifact.exception.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.npm.artifact.repository.NpmLocalRepository
import com.tencent.bkrepo.npm.artifact.repository.NpmRemoteRepository
import com.tencent.bkrepo.npm.artifact.repository.NpmVirtualRepository
import com.tencent.bkrepo.npm.pojo.NpmErrorResponse
import com.tencent.bkrepo.npm.pojo.OhpmResponse
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component

@Component
class NpmArtifactConfigurer : ArtifactConfigurerSupport() {

    override fun getRepositoryTypes() = listOf(RepositoryType.OHPM)
    override fun getRepositoryType() = RepositoryType.NPM
    override fun getLocalRepository() = SpringContextUtils.getBean<NpmLocalRepository>()
    override fun getRemoteRepository() = SpringContextUtils.getBean<NpmRemoteRepository>()
    override fun getVirtualRepository() = SpringContextUtils.getBean<NpmVirtualRepository>()
    override fun getAuthSecurityCustomizer() = HttpAuthSecurityCustomizer { httpAuthSecurity ->
        val authenticationManager = httpAuthSecurity.authenticationManager!!
        val jwtAuthProperties = httpAuthSecurity.jwtAuthProperties!!
        val npmLoginAuthHandler = NpmLoginAuthHandler(authenticationManager, jwtAuthProperties)
        httpAuthSecurity.withPrefix("/npm").addHttpAuthHandler(npmLoginAuthHandler)
    }

    override fun getExceptionResponseTranslator() = object : ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse): Any {
            return if (ArtifactContextHolder.getRepoDetailOrNull()?.type == RepositoryType.OHPM) {
                OhpmResponse.error(HttpContextHolder.getResponse().status, payload.message.orEmpty())
            } else {
                NpmErrorResponse(payload.message.orEmpty(), StringPool.EMPTY)
            }
        }
    }
}
