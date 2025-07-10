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
import com.tencent.bkrepo.auth.pojo.token.CredentialSet
import io.swagger.v3.oas.annotations.media.Schema

import java.time.LocalDateTime

@Schema(title = "账号信息")
data class Account(
    @get:Schema(title = "id")
    val id: String,
    @get:Schema(title = "appId")
    val appId: String,
    @get:Schema(title = "locked状态")
    val locked: Boolean,
    @get:Schema(title = "认证ak/sk对")
    val credentials: List<CredentialSet>,
    @get:Schema(title = "应用所有人")
    val owner: String?,
    @get:Schema(title = "授权方式")
    var authorizationGrantTypes: Set<AuthorizationGrantType>,
    @get:Schema(title = "主页地址")
    var homepageUrl: String?,
    @get:Schema(title = "回调地址")
    var redirectUri: String?,
    @get:Schema(title = "图标地址")
    var avatarUrl: String?,
    @get:Schema(title = "权限类型")
    var scope: Set<ResourceType>?,
    @get:Schema(title = "权限范围")
    var scopeDesc: List<ScopeRule>?,
    @get:Schema(title = "描述信息")
    var description: String?,
    @get:Schema(title = "创建时间")
    var createdDate: LocalDateTime?,
    @get:Schema(title = "最后修改时间")
    var lastModifiedDate: LocalDateTime?
)
