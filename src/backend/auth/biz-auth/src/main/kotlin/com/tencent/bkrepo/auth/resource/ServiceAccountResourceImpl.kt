package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceAccountResource
import com.tencent.bkrepo.auth.pojo.Account
import com.tencent.bkrepo.auth.pojo.CreateAccountRequest
import com.tencent.bkrepo.auth.pojo.CredentialSet
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceAccountResourceImpl @Autowired constructor(
    private val accountService: AccountService
) : ServiceAccountResource {

    override fun listAccount(): Response<List<Account>> {
        val accountList  = accountService.listAccount()
        return Response(accountList)
    }

    override fun createAccount(request: CreateAccountRequest): Response<Boolean> {
        accountService.createAccount(request)
        return Response(true)
    }

    override fun updateAccount(account: String, locked: Boolean): Response<Boolean> {
        accountService.updateAccountStatus(account,locked)
        return Response(true)
    }

    override fun deleteAccount(account: String): Response<Boolean> {
        accountService.deleteAccount(account)
        return Response(true)
    }

    override fun getCredential(account: String): Response<List<CredentialSet>> {
        val credential = accountService.listCredentials(account)
        return Response(credential)
    }

    override fun createCredential(account: String): Response<Boolean> {
        accountService.createCredential(account)
        return Response(true)
    }


    override fun deleteCredential(account: String, accesskey: String): Response<Boolean> {
        accountService.deleteCredential(account,accesskey)
        return Response(true)
    }

    override fun updateCredential(account: String, accesskey: String, status: CredentialStatus): Response<Boolean> {
        accountService.updateCredentialStatus(account,accesskey,status)
        return Response(true)
    }

    override fun checkCredential(accesskey: String, secretkey: String): Response<Boolean> {
        val result = accountService.checkCredential(accesskey,secretkey)
        return Response(result)
    }
}