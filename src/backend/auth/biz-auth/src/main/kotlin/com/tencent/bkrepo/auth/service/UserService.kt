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

package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.auth.pojo.token.TokenResult
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToRepoRequest
import com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.api.pojo.Page

interface UserService {

    fun getUserById(userId: String): User?

    fun createUser(request: CreateUserRequest): Boolean

    fun createUserToProject(request: CreateUserToProjectRequest): Boolean

    fun createUserToRepo(request: CreateUserToRepoRequest): Boolean

    fun listUser(rids: List<String>, tenantId: String?): List<User>

    fun deleteById(userId: String): Boolean

    fun updateUserById(userId: String, request: UpdateUserRequest): Boolean

    fun addUserToRole(userId: String, roleId: String): User?

    fun addUserToRoleBatch(idList: List<String>, roleId: String): Boolean

    fun removeUserFromRole(userId: String, roleId: String): User?

    fun removeUserFromRoleBatch(idList: List<String>, roleId: String): Boolean

    fun createToken(userId: String): Token?

    fun createOrUpdateUser(userId: String, name: String, tenantId: String?)

    fun addUserToken(userId: String, name: String, expiredAt: String?): Token?

    fun listUserToken(userId: String): List<TokenResult>

    fun listValidToken(userId: String): List<Token>

    fun removeToken(userId: String, name: String): Boolean

    fun findUserByUserToken(userId: String, pwd: String): User?

    fun userPage(pageNumber: Int, pageSize: Int, userName: String?, admin: Boolean?, locked: Boolean?): Page<UserInfo>

    fun getUserInfoById(userId: String): UserInfo?

    fun getUserInfoByToken(token: String): UserInfo?

    fun getUserPwdById(userId: String): String?

    fun updatePassword(userId: String, oldPwd: String, newPwd: String): Boolean

    fun resetPassword(userId: String): Boolean

    fun repeatUid(userId: String): Boolean

    fun addUserAccount(userId: String, accountId: String): Boolean

    fun removeUserAccount(userId: String, accountId: String): Boolean

    fun validateEntityUser(userId: String): Boolean

    fun getRelatedUserById(userId: String): List<UserInfo>

    fun listAdminUsers(): List<String>
}
