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

package com.tencent.bkrepo.common.service.otel.web

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.cloud.sleuth.Span
import org.springframework.cloud.sleuth.Tracer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import javax.servlet.Filter
import javax.servlet.http.HttpServletResponse

@Configuration
@ConditionalOnProperty(value = ["spring.sleuth.enabled"], matchIfMissing = true)
class OtelWebConfiguration  {

    @Bean
    fun otelWebFilter(): FilterRegistrationBean<OtelWebFilter> {
        val registrationBean = FilterRegistrationBean<OtelWebFilter>()
        registrationBean.filter = OtelWebFilter()
        registrationBean.order = Ordered.HIGHEST_PRECEDENCE
        registrationBean.addUrlPatterns("/*")
        return registrationBean
    }

    @Bean
    fun traceIdInResponseFilter(tracer: Tracer): Filter {
        return Filter { request, response, chain ->
            val currentSpan: Span? = tracer.currentSpan()
            if (currentSpan != null) {
                val resp = response as HttpServletResponse
                resp.addHeader(HEADER_TRACE_ID, currentSpan.context().traceId())
                resp.addHeader(HEADER_BKREPO_TRACE_ID, tracer.getBaggage(TRACE_ID_BAGGAGE_KEY)?.get().toString())
            }
            chain.doFilter(request, response)
        }
    }

    companion object {
        const val HEADER_TRACE_ID = "Trace-Id"
        const val HEADER_BKREPO_TRACE_ID = "X-BkRepo-Trace-Id"
        const val TRACE_ID_BAGGAGE_KEY = "X-BKREPO-TRACE-ID"
    }
}
