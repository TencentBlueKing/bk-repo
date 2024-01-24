/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.security.manager

import com.tencent.bkrepo.auth.api.ServiceAccountClient
import com.tencent.bkrepo.auth.api.ServiceOauthAuthorizationClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import com.tencent.bkrepo.auth.pojo.oauth.OauthToken
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import org.springframework.stereotype.Component

/**
 * 认证管理器
 */
@Component
class AuthenticationManager(
    private val serviceUserClient: ServiceUserClient,
    private val serviceAccountClient: ServiceAccountClient,
    private val serviceOauthAuthorizationClient: ServiceOauthAuthorizationClient
) {

    /**
     * 校验普通用户类型账户
     * @throws AuthenticationException 校验失败
     */
    fun checkUserAccount(uid: String, token: String): String {
        val response = serviceUserClient.checkToken(uid, token)
        return if (response.data == true) uid else throw AuthenticationException("Authorization value check failed")
    }

    /**
     * 校验平台账户
     * @throws AuthenticationException 校验失败
     */
    fun checkPlatformAccount(accessKey: String, secretKey: String): String {
        val response = serviceAccountClient.checkAccountCredential(
            accesskey = accessKey,
            secretkey = secretKey,
            authorizationGrantType = AuthorizationGrantType.PLATFORM
        )
        return response.data ?: throw AuthenticationException("AccessKey/SecretKey check failed.")
    }

    /**
     * 校验Oauth Token
     */
    fun checkOauthToken(accessToken: String): String {
        val response = serviceOauthAuthorizationClient.validateToken(accessToken)
        return response.data ?: throw AuthenticationException("Access token check failed.")
    }

    /**
     * 普通用户类型账户
     */
    fun createUserAccount(userId: String) {
        val request = CreateUserRequest(userId = userId, name = userId)
        serviceUserClient.createUser(request)
    }

    /**
     * 根据用户id[userId]查询用户信息
     * 当用户不存在时返回`null`
     */
    fun findUserAccount(userId: String): UserInfo? {
        return serviceUserClient.userInfoById(userId).data
    }
    /**
     * 根据用户id[userId]查询用户密码
     * 当用户不存在时返回`null`
     */
    fun findUserPwd(userId: String): String? {
        return serviceUserClient.userPwdById(userId).data
    }

    /**
     * 根据用户id[userId]查询用户token
     */
    fun findUserToken(userId: String): List<String>? {
        return serviceUserClient.userTokenById(userId).data
    }

    fun findOauthToken(accessToken: String): OauthToken? {
        return serviceOauthAuthorizationClient.getToken(accessToken).data
    }

    /**
     * 根据appId和ak查找sk
     * */
    fun findSecretKey(appId: String, accessKey: String): String? {
        return serviceAccountClient.findSecretKey(appId, accessKey).data
    }

}
