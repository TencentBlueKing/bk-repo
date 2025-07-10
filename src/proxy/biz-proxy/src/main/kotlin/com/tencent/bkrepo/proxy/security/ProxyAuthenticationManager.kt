/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.proxy.security

import com.tencent.bkrepo.auth.api.proxy.ProxyAccountClient
import com.tencent.bkrepo.auth.api.proxy.ProxyOauthAuthorizationClient
import com.tencent.bkrepo.auth.api.proxy.ProxyTemporaryTokenClient
import com.tencent.bkrepo.auth.api.proxy.ProxyUserClient
import com.tencent.bkrepo.auth.pojo.oauth.OauthToken
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.service.proxy.ProxyFeignClientFactory

class ProxyAuthenticationManager : AuthenticationManager() {
    private val proxyUserClient: ProxyUserClient by lazy { ProxyFeignClientFactory.create("auth") }
    private val proxyAccountClient: ProxyAccountClient by lazy { ProxyFeignClientFactory.create("auth") }
    private val proxyOauthAuthorizationClient: ProxyOauthAuthorizationClient
        by lazy { ProxyFeignClientFactory.create("auth") }
    private val proxyTemporaryTokenClient: ProxyTemporaryTokenClient
        by lazy { ProxyFeignClientFactory.create("auth") }

    /**
     * 校验普通用户类型账户
     * @throws AuthenticationException 校验失败
     */
    override fun checkUserAccount(uid: String, token: String): String {
        val response = proxyUserClient.checkToken(uid, token)
        return if (response.data == true) uid else throw AuthenticationException("Authorization value check failed")
    }

    /**
     * 校验平台账户
     * @throws AuthenticationException 校验失败
     */
    override fun checkPlatformAccount(accessKey: String, secretKey: String): String {
        val response = proxyAccountClient.checkAccountCredential(accessKey, secretKey)
        return response.data ?: throw AuthenticationException("AccessKey/SecretKey check failed.")
    }

    /**
     * 校验Oauth Token
     */
    override fun checkOauthToken(accessToken: String): String {
        val response = proxyOauthAuthorizationClient.validateToken(accessToken)
        return response.data ?: throw AuthenticationException("Access token check failed.")
    }

    /**
     * 普通用户类型账户
     */
    override fun createUserAccount(userId: String) {
        val request = CreateUserRequest(userId = userId, name = userId)
        proxyUserClient.createUser(request)
    }

    /**
     * 根据用户id[userId]查询用户信息
     * 当用户不存在时返回`null`
     */
    override fun findUserAccount(userId: String): UserInfo? {
        return proxyUserClient.userInfoById(userId).data
    }

    override fun findOauthToken(accessToken: String): OauthToken? {
        return proxyOauthAuthorizationClient.getToken(accessToken).data
    }

    /**
     * 根据appId和ak查找sk
     * */
    override fun findSecretKey(appId: String, accessKey: String): String? {
        return proxyAccountClient.findSecretKey(appId, accessKey).data
    }

    override fun getTokenInfo(token: String): TemporaryTokenInfo? {
        return proxyTemporaryTokenClient.getTokenInfo(token).data
    }
}
