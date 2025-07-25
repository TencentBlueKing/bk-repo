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

package com.tencent.bkrepo.common.artifact.config

import com.tencent.bkrepo.common.artifact.exception.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer

/**
 * 依赖源配置器
 */
interface ArtifactConfigurer {

    /**
     * 依赖源类型[RepositoryType]
     */
    fun getRepositoryType(): RepositoryType

    /**
     * 依赖源类型List[RepositoryType]
     * 一个仓库支持多种类型
     */
    fun getRepositoryTypes(): List<RepositoryType> { return emptyList() }

    /**
     * 本地仓库实现逻辑[LocalRepository]
     */
    fun getLocalRepository(): LocalRepository

    /**
     * 远程仓库实现逻辑[RemoteRepository]
     */
    fun getRemoteRepository(): RemoteRepository

    /**
     * 虚拟仓库实现逻辑[VirtualRepository]
     */
    fun getVirtualRepository(): VirtualRepository

    /**
     * HttpAuthSecurity 自定义配置器
     */
    fun getAuthSecurityCustomizer(): HttpAuthSecurityCustomizer

    /**
     * 异常消息响应体格式转换器
     */
    fun getExceptionResponseTranslator(): ExceptionResponseTranslator
}
