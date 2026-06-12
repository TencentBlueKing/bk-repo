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

package com.tencent.bkrepo.opdata.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurerSupport
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.opdata.security.ResilientPlatformAuthHandler
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class OpDataConfigurer : ArtifactConfigurerSupport() {

    override fun getRepositoryType() = RepositoryType.NONE
    override fun getLocalRepository(): LocalRepository = object : LocalRepository() {}
    override fun getRemoteRepository(): RemoteRepository = object : RemoteRepository() {}
    override fun getVirtualRepository(): VirtualRepository = object : VirtualRepository() {}

    override fun getAuthSecurityCustomizer() =
        HttpAuthSecurityCustomizer { httpAuthSecurity ->
            httpAuthSecurity.withPrefix("/opdata")
            // 关闭默认 PlatformAuthHandler，改用容错版本：
            // 当 auth 微服务不可用（Feign 连接/超时/5xx）时降级为匿名，
            // 避免整个 opdata 因为 auth 故障而不可用。
            // 注意：此 lambda 在 HttpAuthSecurityConfiguration 中执行，
            // 此时 httpAuthSecurity.authenticationManager 已被框架注入，可直接使用。
            httpAuthSecurity.disablePlatformAuth()
            val authenticationManager = httpAuthSecurity.authenticationManager
                ?: error("AuthenticationManager not initialized on HttpAuthSecurity")
            httpAuthSecurity.addHttpAuthHandler(ResilientPlatformAuthHandler(authenticationManager))
        }
}
