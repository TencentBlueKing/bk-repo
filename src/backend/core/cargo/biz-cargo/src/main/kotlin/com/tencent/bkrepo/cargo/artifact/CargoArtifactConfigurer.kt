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

package com.tencent.bkrepo.cargo.artifact

import com.tencent.bkrepo.cargo.artifact.repository.CargoLocalRepository
import com.tencent.bkrepo.cargo.artifact.repository.CargoRemoteRepository
import com.tencent.bkrepo.cargo.artifact.repository.CargoVirtualRepository
import com.tencent.bkrepo.cargo.config.CargoProperties
import com.tencent.bkrepo.cargo.pojo.CargoErrorDetail
import com.tencent.bkrepo.cargo.pojo.CargoErrorResponse
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurerSupport
import com.tencent.bkrepo.common.artifact.exception.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse

@Configuration
@EnableConfigurationProperties(CargoProperties::class)
class CargoArtifactConfigurer : ArtifactConfigurerSupport() {

    override fun getRepositoryType() = RepositoryType.CARGO
    override fun getLocalRepository() = SpringContextUtils.getBean<CargoLocalRepository>()
    override fun getRemoteRepository() = SpringContextUtils.getBean<CargoRemoteRepository>()
    override fun getVirtualRepository() = SpringContextUtils.getBean<CargoVirtualRepository>()
    override fun getAuthSecurityCustomizer() =
        HttpAuthSecurityCustomizer { httpAuthSecurity ->
            httpAuthSecurity
                .withPrefix("/cargo")
                .excludePattern("/**/index/config.json")
        }

    override fun getExceptionResponseTranslator() = object : ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse): Any {
            return CargoErrorResponse(listOf(CargoErrorDetail(payload.message.orEmpty())))
        }
    }
}
