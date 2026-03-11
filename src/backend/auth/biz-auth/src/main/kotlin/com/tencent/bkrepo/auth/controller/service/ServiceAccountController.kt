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

package com.tencent.bkrepo.auth.controller.service

import com.tencent.bkrepo.auth.api.ServiceAccountClient
import com.tencent.bkrepo.auth.controller.OpenResource
import com.tencent.bkrepo.auth.pojo.account.AccountInfo
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceAccountController @Autowired constructor(
    private val accountService: AccountService,
    permissionService: PermissionService
) : ServiceAccountClient, OpenResource(permissionService) {

    @Deprecated("删除get方式校验")
    override fun checkCredential(accesskey: String, secretkey: String): Response<String?> {
        val result = accountService.checkCredential(accesskey, secretkey, null)
        return ResponseBuilder.success(result)
    }

    override fun checkAccountCredential(
        accesskey: String,
        secretkey: String,
        authorizationGrantType: AuthorizationGrantType?
    ): Response<String?> {
        val result = accountService.checkCredential(accesskey, secretkey, authorizationGrantType)
        return ResponseBuilder.success(result)
    }

    override fun findSecretKey(appId: String, accessKey: String): Response<String?> {
        val result = accountService.findSecretKey(appId, accessKey)
        return ResponseBuilder.success(result)
    }

    override fun listAccountsForFederation(): Response<List<AccountInfo>> {
        val accounts = accountService.listAccount(displaySecretKey = true)
        val result = accounts.map { acc ->
            AccountInfo(
                id = acc.id,
                appId = acc.appId,
                locked = acc.locked,
                authorizationGrantTypes = acc.authorizationGrantTypes.map { it.name }.toSet(),
                homepageUrl = acc.homepageUrl,
                redirectUri = acc.redirectUri,
                avatarUrl = acc.avatarUrl,
                scope = acc.scope?.map { it.name }?.toSet(),
                description = acc.description,
                credentials = acc.credentials
            )
        }
        return ResponseBuilder.success(result)
    }

    override fun createAccountForFederation(accountInfo: AccountInfo): Response<Boolean> {
        accountService.upsertAccountForFederation(accountInfo)
        return ResponseBuilder.success(true)
    }

    override fun updateAccountForFederation(accountInfo: AccountInfo): Response<Boolean> {
        accountService.upsertAccountForFederation(accountInfo)
        return ResponseBuilder.success(true)
    }

    override fun deleteAccountForFederation(appId: String): Response<Boolean> {
        val result = accountService.deleteAccount(appId, "")
        return ResponseBuilder.success(result)
    }

    override fun upsertAccountForFederation(accountInfo: AccountInfo): Response<Void> {
        accountService.upsertAccountForFederation(accountInfo)
        return ResponseBuilder.success()
    }
}
