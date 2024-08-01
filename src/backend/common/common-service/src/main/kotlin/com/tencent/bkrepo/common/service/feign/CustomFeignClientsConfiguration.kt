/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.service.feign

import com.tencent.bkrepo.common.api.constant.COMMIT_EDGE_HEADER
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import feign.RequestInterceptor
import feign.Retryer
import feign.codec.Decoder
import feign.codec.Encoder
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cloud.openfeign.FeignClientsConfiguration
import org.springframework.cloud.openfeign.FeignLoggerFactory
import org.springframework.cloud.openfeign.support.AbstractFormWriter
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(FeignClientsConfiguration::class)
class CustomFeignClientsConfiguration {

    @Bean
    fun requestInterceptor(): RequestInterceptor {
        return RequestInterceptor { requestTemplate ->
            // 设置Accept-Language请求头
            HttpContextHolder.getRequestOrNull()?.getHeader(HttpHeaders.ACCEPT_LANGUAGE)?.let {
                requestTemplate.header(HttpHeaders.ACCEPT_LANGUAGE, it)
            }
            HttpContextHolder.getRequestOrNull()?.getHeader(COMMIT_EDGE_HEADER)?.let {
                requestTemplate.header(COMMIT_EDGE_HEADER, it)
            }
        }
    }

    @Bean
    fun errorCodeDecoder() = ErrorCodeDecoder()

    @Bean
    fun feignClientsConfiguration() = FeignClientsConfiguration()

    @Bean
    @ConditionalOnMissingBean
    fun feignLoggerFactory(feignClientsConfiguration: FeignClientsConfiguration): FeignLoggerFactory {
        return feignClientsConfiguration.feignLoggerFactory()
    }

    @Bean
    @ConditionalOnMissingBean
    fun feignEncoder(
        formWriterProvider: ObjectProvider<AbstractFormWriter>,
        customizers: ObjectProvider<HttpMessageConverterCustomizer>,
        feignClientsConfiguration: FeignClientsConfiguration,
    ): Encoder {
        return feignClientsConfiguration.feignEncoder(formWriterProvider, customizers)
    }

    @Bean
    @ConditionalOnMissingBean
    fun feignRetryer(feignClientsConfiguration: FeignClientsConfiguration): Retryer {
        return feignClientsConfiguration.feignRetryer()
    }

    @Bean
    @ConditionalOnMissingBean
    fun feignDecoder(
        customizers: ObjectProvider<HttpMessageConverterCustomizer>,
        feignClientsConfiguration: FeignClientsConfiguration,
    ): Decoder {
        return feignClientsConfiguration.feignDecoder(customizers)
    }
}
