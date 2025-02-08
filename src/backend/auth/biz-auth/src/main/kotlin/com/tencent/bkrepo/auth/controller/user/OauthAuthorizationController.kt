/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.controller.user

import com.tencent.bkrepo.auth.constant.AUTH_API_OAUTH_PREFIX
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizeRequest
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizedResult
import com.tencent.bkrepo.auth.pojo.oauth.GenerateTokenRequest
import com.tencent.bkrepo.auth.pojo.oauth.JsonWebKeySet
import com.tencent.bkrepo.auth.pojo.oauth.OauthToken
import com.tencent.bkrepo.auth.pojo.oauth.OidcConfiguration
import com.tencent.bkrepo.auth.pojo.oauth.UserInfo
import com.tencent.bkrepo.auth.service.OauthAuthorizationService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(AUTH_API_OAUTH_PREFIX)
class OauthAuthorizationController @Autowired constructor(
    private val oauthAuthorizationService: OauthAuthorizationService
) {

    @ApiOperation("用户确认Oauth授权")
    @GetMapping("/authorize")
    fun authorize(
        @RequestParam("client_id") clientId: String,
        state: String,
        scope: String?,
        nonce: String?,
        @RequestParam("code_challenge") codeChallenge: String?,
        @RequestParam("code_challenge_method") codeChallengeMethod: String?
    ): Response<AuthorizedResult> {
        val request = AuthorizeRequest(
            clientId = clientId,
            state = state,
            scope = scope,
            nonce = nonce,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod
        )
        val authorizedResult = oauthAuthorizationService.authorized(request)
        return ResponseBuilder.success(authorizedResult)
    }

    @ApiOperation("获取oauth token信息")
    @GetMapping("/token")
    fun getToken(accessToken: String): Response<OauthToken?> {
        return ResponseBuilder.success(oauthAuthorizationService.getToken(accessToken))
    }

    @ApiOperation("创建或刷新oauth token")
    @PostMapping("/{projectId}/token")
    fun generateToken(
        code: String?,
        @RequestParam("grant_type") grantType: String,
        @RequestParam("client_id") clientId: String?,
        @RequestParam("client_secret") clientSecret: String?,
        @RequestParam("refresh_token") refreshToken: String?,
        scope: String?,
        @RequestParam("code_verifier") codeVerifier: String?,
    ) {
        val request = GenerateTokenRequest(code, grantType, clientId, clientSecret, refreshToken, scope, codeVerifier)
        if (request.grantType == "refresh_token") {
            oauthAuthorizationService.refreshToken(request)
        } else {
            oauthAuthorizationService.createToken(request)
        }
    }

    @ApiOperation("删除oauth token")
    @DeleteMapping("/token")
    fun deleteToken(clientId: String, clientSecret: String, accessToken: String): Response<Void> {
        oauthAuthorizationService.deleteToken(clientId, clientSecret, accessToken)
        return ResponseBuilder.success()
    }

    @ApiOperation("验证oauth token")
    @GetMapping("/token/validate")
    fun validateToken(accessToken: String): Response<String?> {
        return ResponseBuilder.success(oauthAuthorizationService.validateToken(accessToken))
    }

    @ApiOperation("用户信息")
    @GetMapping("/{projectId}/userInfo")
    fun userInfo(): UserInfo {
        return oauthAuthorizationService.getUserInfo()
    }

    @ApiOperation("OpenId Connect配置")
    @GetMapping("/{projectId}/.well-known/openid-configuration")
    fun configuration(@PathVariable projectId: String): OidcConfiguration {
        return oauthAuthorizationService.getOidcConfiguration(projectId)
    }

    @ApiOperation("Json web key set")
    @GetMapping("/{projectId}/.well-known/jwks.json")
    fun jwks(): JsonWebKeySet {
        return oauthAuthorizationService.getJwks()
    }
}
