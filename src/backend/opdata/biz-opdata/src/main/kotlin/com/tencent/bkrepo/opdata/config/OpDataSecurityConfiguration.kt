/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
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

import com.tencent.bkrepo.common.security.spi.UserAuthProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * opdata 模块的安全相关自定义配置。
 *
 * 通过提供自定义 [UserAuthProvider] Bean 覆盖 common-security 默认的 Feign 实现，
 * 使 opdata 的 `@Principal(ADMIN)` 鉴权及用户信息查询不再依赖 auth 微服务，提升可用性。
 */
@Configuration
class OpDataSecurityConfiguration {

    /**
     * 注册 [LocalUserService]，同时用作 [UserAuthProvider] 的实现。
     * 使用 primary 容器 bean，确保 `common-security` 自动装配时优先使用本地实现。
     */
    @Bean
    fun localUserService(mongoTemplate: MongoTemplate): LocalUserService {
        return LocalUserService(mongoTemplate)
    }

    @Bean
    fun userAuthProvider(localUserService: LocalUserService): UserAuthProvider {
        return localUserService
    }
}
