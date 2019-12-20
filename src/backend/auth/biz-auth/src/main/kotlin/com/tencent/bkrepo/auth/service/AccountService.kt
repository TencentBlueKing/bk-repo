package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.*
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import javax.crypto.SecretKey


interface AccountService {

    fun listAccount(): List<Account>

    fun createAccount(request: CreateAccountRequest): Boolean?

    fun deleteAccount(appId: String): Boolean

    fun updateAccountStatus(appId: String, locked: Boolean): Boolean

    fun createCredential(appId: String): Boolean

    fun listCredentials(appId: String): List<CredentialSet>

    fun deleteCredential(appId: String, accessKey: String): Boolean

    fun updateCredentialStatus(appId: String, accessKey: String, status: CredentialStatus): Boolean

    fun checkCredential(accessKey: String, secretKey: String): Boolean
}