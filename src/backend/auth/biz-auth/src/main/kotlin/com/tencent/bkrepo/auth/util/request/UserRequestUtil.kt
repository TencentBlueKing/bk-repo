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

package com.tencent.bkrepo.auth.util.request

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.auth.pojo.token.TokenResult
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToRepoRequest
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.auth.util.IDUtil
import com.tencent.bkrepo.common.api.constant.StringPool
import java.time.LocalDateTime

object UserRequestUtil {

    fun convToCreateRepoUserRequest(request: CreateUserToRepoRequest): CreateUserRequest {
        return CreateUserRequest(
            request.userId,
            request.name,
            request.pwd,
            request.admin,
            request.asstUsers,
            request.group
        )
    }

    fun convTokenResult(tokens: List<Token>): List<TokenResult> {
        return tokens.map { TokenResult(it.name, it.createdAt, it.expiredAt) }
    }

    fun convTokenInfo(tokens: List<Token>): List<Token> {
        return tokens.map { Token(it.name, StringPool.EMPTY, it.createdAt, it.expiredAt) }
    }

    fun convToCreateProjectUserRequest(request: CreateUserToProjectRequest): CreateUserRequest {
        return CreateUserRequest(
            request.userId,
            request.name,
            request.pwd,
            request.admin,
            request.asstUsers,
            request.group
        )
    }

    fun convToTUser(request: CreateUserRequest, hashPwd: String, tenantId: String?): TUser {
        return TUser(
            userId = request.userId,
            name = request.name,
            pwd = hashPwd,
            admin = request.admin,
            locked = false,
            tokens = emptyList(),
            roles = emptyList(),
            asstUsers = request.asstUsers,
            group = request.group,
            email = request.email,
            phone = request.phone,
            createdDate = LocalDateTime.now(),
            lastModifiedDate = LocalDateTime.now(),
            tenantId = tenantId
        )
    }

    fun convToUser(user: TUser): User {
        return User(
            userId = user.userId,
            name = user.name,
            pwd = StringPool.EMPTY,
            admin = user.admin,
            locked = user.locked,
            tokens = convTokenInfo(user.tokens),
            roles = user.roles
        )
    }

    fun convToUserInfo(user: TUser): UserInfo {
        return UserInfo(
            userId = user.userId,
            name = user.name,
            locked = user.locked,
            email = user.email,
            phone = user.phone,
            createdDate = user.createdDate,
            admin = user.admin,
            group = user.group,
            asstUsers = user.asstUsers
        )
    }

    fun matchToken(pwd: String, hashPwd: String, token: String): Boolean {
        if (token == pwd || token == hashPwd) return true
        return false
    }

    fun generateToken(): String {
        return DataDigestUtils.md5FromStr(IDUtil.genRandomId())
    }
}
