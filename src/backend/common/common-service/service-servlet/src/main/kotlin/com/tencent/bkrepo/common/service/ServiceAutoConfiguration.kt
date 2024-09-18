/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.service

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.actuator.ActuatorConfiguration
import com.tencent.bkrepo.common.service.async.AsyncConfiguration
import com.tencent.bkrepo.common.service.cluster.ClusterConfiguration
import com.tencent.bkrepo.common.service.cluster.StandaloneJobAspect
import com.tencent.bkrepo.common.service.exception.GlobalExceptionHandler
import com.tencent.bkrepo.common.service.exception.ServiceExceptionHandler
import com.tencent.bkrepo.common.service.feign.ClientConfiguration
import com.tencent.bkrepo.common.service.feign.CustomFeignClientsConfiguration
import com.tencent.bkrepo.common.service.log.AccessLogWebServerCustomizer
import com.tencent.bkrepo.common.service.message.MessageSourceConfiguration
import com.tencent.bkrepo.common.service.metrics.UndertowMetrics
import com.tencent.bkrepo.common.service.otel.mongodb.OtelMongoConfiguration
import com.tencent.bkrepo.common.service.otel.web.OtelWebConfiguration
import com.tencent.bkrepo.common.service.shutdown.ServiceShutdownConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.core.Ordered
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

@Configuration
@PropertySource("classpath:common-service.properties")
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
@Import(
    ActuatorConfiguration::class,
    GlobalExceptionHandler::class,
    ServiceExceptionHandler::class,
    AsyncConfiguration::class,
    MessageSourceConfiguration::class,
    ClientConfiguration::class,
    AccessLogWebServerCustomizer::class,
    UndertowMetrics::class,
    ServiceShutdownConfiguration::class,
    ClusterConfiguration::class,
    CustomFeignClientsConfiguration::class,
    OtelMongoConfiguration::class,
    OtelWebConfiguration::class,
    StandaloneJobAspect::class
)
class ServiceAutoConfiguration {

    @Bean
    fun objectMapper() = JsonUtils.objectMapper

    @Bean
    fun mappingJackson2HttpMessageConverter(): MappingJackson2HttpMessageConverter {
        return MappingJackson2HttpMessageConverter(JsonUtils.objectMapper)
    }
}
