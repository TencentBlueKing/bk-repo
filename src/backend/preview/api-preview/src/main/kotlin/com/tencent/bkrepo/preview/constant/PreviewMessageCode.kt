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

package com.tencent.bkrepo.preview.constant

import com.tencent.bkrepo.common.api.message.MessageCode

enum class PreviewMessageCode(private val key: String) : MessageCode {
    PREVIEW_FILE_NOT_FOUND("preview.file.not-found"),
    PREVIEW_NODE_NOT_FOUND("preview.node.not-found"),
    PREVIEW_REPO_NOT_FOUND("preview.repo.not-found"),
    PREVIEW_FIlE_CONVERT_ERROR("preview.file.convert.error"),
    PREVIEW_FILE_SIZE_LIMIT_ERROR("preview.file.size.limit.error"),
    PREVIEW_FILE_NOT_SUPPORT_ERROR("preview.file.not-support"),
    PREVIEW_PARAMETER_INVALID("preview.parameter.invalid"),

    /**
     * 临时 token 越权：访问的 fullPath / projectId / repoName 不在 token 声明范围内。
     * 由 PreviewTokenAuthService 触发。
     */
    PREVIEW_TEMPORARY_TOKEN_OUT_OF_SCOPE("preview.temporary-token.out-of-scope"),

    /**
     * 定向分享模式下，请求携带的网关登录用户不在 token 的 authorizedUserList 内。
     */
    PREVIEW_TEMPORARY_TOKEN_USER_FORBIDDEN("preview.temporary-token.user-forbidden"),

    /**
     * token 的 permits 剩余次数为 0。
     */
    PREVIEW_TEMPORARY_TOKEN_PERMITS_EXHAUSTED("preview.temporary-token.permits-exhausted"),

    /**
     * 定向分享模式下访客未登录，需先跳转网关登录页。
     */
    PREVIEW_LOGIN_REQUIRED("preview.login.required")
    ;

    override fun getBusinessCode() = ordinal + 1
    override fun getKey() = key
    override fun getModuleCode() = 26
}
