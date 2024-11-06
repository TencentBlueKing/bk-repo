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

package com.tencent.bkrepo.lfs.security

import com.tencent.bkrepo.common.api.constant.BASIC_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.util.BasicAuthUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.basic.BasicAuthCredentials
import com.tencent.bkrepo.common.security.http.core.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.lfs.constant.HEADER_BATCH_AUTHORIZATION
import javax.servlet.http.HttpServletRequest

class RemoteAuthHandler : HttpAuthHandler {
    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val repoDetail = ArtifactContextHolder.getRepoDetailOrNull()
        if (repoDetail != null && repoDetail.category != RepositoryCategory.REMOTE) {
            return AnonymousCredentials()
        }
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        val batchAuthorizationHeader = request.getHeader(HEADER_BATCH_AUTHORIZATION).orEmpty()
        return if (authorizationHeader.startsWith(BASIC_AUTH_PREFIX)) {
            try {
                val pair = BasicAuthUtils.decode(authorizationHeader)
                BasicAuthCredentials(pair.first, pair.second)
            } catch (ignored: IllegalArgumentException) {
                throw AuthenticationException("Invalid authorization value.")
            }
        } else if (batchAuthorizationHeader.isNotBlank()) {
            try {
                val pair = BasicAuthUtils.decode(batchAuthorizationHeader)
                BasicAuthCredentials(pair.first, pair.second)
            } catch (ignored: IllegalArgumentException) {
                throw AuthenticationException("Invalid authorization value.")
            }
        } else {
            AnonymousCredentials()
        }
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        require(authCredentials is BasicAuthCredentials)
        return authCredentials.username
    }
}
