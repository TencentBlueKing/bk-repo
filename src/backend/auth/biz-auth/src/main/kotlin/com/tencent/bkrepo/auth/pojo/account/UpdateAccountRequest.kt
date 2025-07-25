/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.pojo.account

import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import io.swagger.v3.oas.annotations.media.Schema


data class UpdateAccountRequest(
    @get:Schema(title = "系统Id")
    val appId: String,
    @get:Schema(title = "是否锁定")
    val locked: Boolean = false,
    @get:Schema(title = "授权方式")
    val authorizationGrantTypes: Set<AuthorizationGrantType>,
    @get:Schema(title = "应用主页")
    val homepageUrl: String? = null,
    @get:Schema(title = "应用回调地址")
    val redirectUri: String? = null,
    @get:Schema(title = "应用图标地址")
    val avatarUrl: String? = null,
    @get:Schema(title = "权限类型")
    val scope: Set<ResourceType>? = null,
    @get:Schema(title = "权限范围")
    val scopeDesc: List<ScopeRule>? = null,
    @get:Schema(title = "简要描述")
    val description: String? = null
)
