package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.*
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus


interface AccountService {

    fun listAccount(): List<Account>

    fun createAccount(request: CreateAccountRequest): Account?

    fun deleteAccount(appId: String): Boolean

    fun updateAccountStatus(appId: String, locked: Boolean): Boolean

    fun createCredential(appId: String): List<CredentialSet>

    fun listCredentials(appId: String): List<CredentialSet>

    fun deleteCredential(appId: String, accessKey: String): List<CredentialSet>

    fun updateCredentialStatus(appId: String, accessKey: String, status: CredentialStatus): Boolean

    fun checkCredential(accessKey: String, secretKey: String): String?
}