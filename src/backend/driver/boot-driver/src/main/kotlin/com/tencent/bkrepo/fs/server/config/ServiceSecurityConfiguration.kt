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

package com.tencent.bkrepo.fs.server.config

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.common.service.exception.GlobalExceptionHandler
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import reactivefeign.client.ReactiveHttpRequestInterceptor

/**
 * 服务间的安全配置
 * */
@Import(ServiceAuthManager::class)
@Configuration
class ServiceSecurityConfiguration {

    @Bean
    fun securityRequestInterceptor(serviceAuthManager: ServiceAuthManager): ReactiveHttpRequestInterceptor {
        return ReactiveHttpRequestInterceptor {
            // 添加微服务间的认证头
            it.headers()[MS_AUTH_HEADER_SECURITY_TOKEN] = listOf(serviceAuthManager.getSecurityToken())
            ReactiveSecurityUtils.getUserMono().map { user ->
                it.headers()[MS_AUTH_HEADER_UID] = listOf(user)
                it
            }
        }
    }

    @Bean
    fun errorWebExceptionHandler(): ErrorWebExceptionHandler {
        return GlobalExceptionHandler()
    }
}
