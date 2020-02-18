package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceAccountResource
import com.tencent.bkrepo.auth.pojo.Account
import com.tencent.bkrepo.auth.pojo.CreateAccountRequest
import com.tencent.bkrepo.auth.pojo.CredentialSet
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceAccountResourceImpl @Autowired constructor(
    private val accountService: AccountService
) : ServiceAccountResource {

    override fun listAccount(): Response<List<Account>> {
        val accountList = accountService.listAccount()
        return ResponseBuilder.success(accountList)
    }

    override fun createAccount(request: CreateAccountRequest): Response<Account?> {
        return ResponseBuilder.success(accountService.createAccount(request))
    }

    override fun updateAccount(appId: String, locked: Boolean): Response<Boolean> {
        accountService.updateAccountStatus(appId, locked)
        return ResponseBuilder.success(true)
    }

    override fun deleteAccount(appId: String): Response<Boolean> {
        accountService.deleteAccount(appId)
        return ResponseBuilder.success(true)
    }

    override fun getCredential(appId: String): Response<List<CredentialSet>> {
        val credential = accountService.listCredentials(appId)
        return ResponseBuilder.success(credential)
    }

    override fun createCredential(appId: String): Response<List<CredentialSet>> {
        val result = accountService.createCredential(appId)
        return ResponseBuilder.success(result)
    }

    override fun deleteCredential(appId: String, accesskey: String): Response<List<CredentialSet>> {
        val result = accountService.deleteCredential(appId, accesskey)
        return ResponseBuilder.success(result)
    }

    override fun updateCredential(appId: String, accesskey: String, status: CredentialStatus): Response<Boolean> {
        accountService.updateCredentialStatus(appId, accesskey, status)
        return ResponseBuilder.success(true)
    }

    override fun checkCredential(accesskey: String, secretkey: String): Response<String?> {
        val result = accountService.checkCredential(accesskey, secretkey)
        return ResponseBuilder.success(result)
    }
}
