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

package com.tencent.bkrepo.common.service.bootstrap

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource
import org.springframework.retry.interceptor.RetryInterceptorBuilder
import org.springframework.retry.interceptor.RetryOperationsInterceptor

@PropertySource("classpath:common-bootstrap.properties")
class CommonBootstrapConfiguration {

    /**
     * 二进制环境未部署config服务时，需要兼容读取consul配置, 所以不能配置spring.cloud.config.failFast=true
     * common-storage模块引入了spring-retry依赖，会导致服务启动不了，报错缺少configServerRetryInterceptor Bean
     */
    @Bean
    fun configServerRetryInterceptor(): RetryOperationsInterceptor {
        return RetryInterceptorBuilder.stateless()
            .backOffOptions(INIT_INTERVAL, MULTIPLIER, MAX_INTERVAL)
            .maxAttempts(MAX_RETRY_TIME)
            .build()
    }

    companion object {
        private const val INIT_INTERVAL = 1000L
        private const val MAX_INTERVAL = 2000L
        private const val MULTIPLIER = 1.2
        private const val MAX_RETRY_TIME = 3
    }
}
