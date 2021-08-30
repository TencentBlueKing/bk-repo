/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.constant.RANDOM_KEY_LENGTH
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TAccount
import com.tencent.bkrepo.auth.pojo.account.Account
import com.tencent.bkrepo.auth.pojo.account.CreateAccountRequest
import com.tencent.bkrepo.auth.pojo.account.UpdateAccountRequest
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import com.tencent.bkrepo.auth.pojo.token.CredentialSet
import com.tencent.bkrepo.auth.repository.AccountRepository
import com.tencent.bkrepo.auth.repository.OauthTokenRepository
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.util.IDUtil
import com.tencent.bkrepo.auth.util.OauthUtils
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.apache.commons.lang.RandomStringUtils
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

class AccountServiceImpl constructor(
    private val accountRepository: AccountRepository,
    private val oauthTokenRepository: OauthTokenRepository,
    private val userService: UserService,
    private val mongoTemplate: MongoTemplate
) : AccountService {

    override fun createAccount(request: CreateAccountRequest): Account {
        logger.info("create  account  request : [$request]")
        if (request.authorizationGrantTypes.contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            Preconditions.checkArgument(!request.homepageUrl.isNullOrBlank(), CreateAccountRequest::homepageUrl.name)
            Preconditions.checkArgument(!request.redirectUri.isNullOrBlank(), CreateAccountRequest::redirectUri.name)
            Preconditions.checkArgument(!request.scope.isNullOrEmpty(), CreateAccountRequest::scope.name)
        }

        val account = accountRepository.findOneByAppId(request.appId)
        account?.let {
            logger.warn("create account [${request.appId}]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_APPID)
        }

        val credentials = mutableListOf<CredentialSet>()
        request.authorizationGrantTypes.forEach {
            credentials.add(
                CredentialSet(
                    accessKey = StringPool.uniqueId(),
                    secretKey = OauthUtils.generateSecretKey(),
                    createdAt = LocalDateTime.now(),
                    status = CredentialStatus.ENABLE,
                    authorizationGrantType = it
                )
            )
        }

        val result = accountRepository.insert(
            TAccount(
                appId = request.appId,
                locked = request.locked,
                credentials = credentials,
                owner = SecurityUtils.getUserId(),
                authorizationGrantTypes = request.authorizationGrantTypes,
                homepageUrl = request.homepageUrl,
                redirectUri = request.redirectUri,
                avatarUrl = request.avatarUrl,
                scope = request.scope,
                description = request.description,
                createdDate = LocalDateTime.now(),
                lastModifiedDate = LocalDateTime.now()
            )
        )
        return transferAccount(result, displaySecretKey = true)
    }

    override fun listAccount(): List<Account> {
        logger.debug("list  account ")
        return accountRepository.findAllBy().map { transferAccount(it) }
    }

    override fun listOwnAccount(): List<Account> {
        val userId = SecurityUtils.getUserId()
        return accountRepository.findByOwner(userId).map { transferAccount(it) }
    }

    override fun listAuthorizedAccount(): List<Account> {
        val userId = SecurityUtils.getUserId()
        val accountIds = oauthTokenRepository.findByUserId(userId).map { it.id!! }
        return accountRepository.findByIdIn(accountIds).map { transferAccount(it) }
    }

    override fun deleteAccount(appId: String): Boolean {
        logger.info("delete account appId : {}", appId)
        val userId = SecurityUtils.getUserId()
        val account = accountRepository.findOneByAppId(appId)
            ?: throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        if (!account.owner.isNullOrBlank() && userId != account.owner) {
            throw ErrorCodeException(AuthMessageCode.AUTH_OWNER_CHECK_FAILED)
        }

        userService.removeUserAccount(userId, account.id!!)
        oauthTokenRepository.deleteByClientId(account.id)
        accountRepository.delete(account)
        return true
    }

    override fun updateAccount(request: UpdateAccountRequest): Boolean {
        logger.info("upload account request : [$request]")
        with(request) {
            val account = accountRepository.findOneByAppId(appId) ?: run {
                logger.warn("update account [$request.appId]  not exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
            }

            account.locked = locked
            account.avatarUrl = avatarUrl ?: account.avatarUrl
            account.homepageUrl = homepageUrl ?: account.homepageUrl
            account.redirectUri = redirectUri ?: account.redirectUri
            account.scope = scope ?: account.scope
            account.description = description ?: account.description
            account.lastModifiedDate = LocalDateTime.now()

            accountRepository.save(account)
            return true
        }
    }

    override fun createCredential(appId: String, type: AuthorizationGrantType): List<CredentialSet> {
        logger.info("create credential appId : $appId , type: $type")
        val account = accountRepository.findOneByAppId(appId) ?: run {
            logger.warn("account [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }
        if (account.authorizationGrantTypes?.contains(type) == false) {
            throw  ErrorCodeException(AuthMessageCode.AUTH_INVALID_TYPE)
        }

        val query = Query.query(Criteria.where(TAccount::appId.name).`is`(appId))
        val update = Update()
        val accessKey = IDUtil.genRandomId()
        val secretKey = RandomStringUtils.randomAlphanumeric(RANDOM_KEY_LENGTH)
        val credentials = CredentialSet(
            accessKey = accessKey,
            secretKey = secretKey,
            createdAt = LocalDateTime.now(),
            status = CredentialStatus.ENABLE,
            authorizationGrantType = type
        )
        update.addToSet("credentials", credentials)
        mongoTemplate.upsert(query, update, TAccount::class.java)
        val result = accountRepository.findOneByAppId(appId) ?: return emptyList()
        return result.credentials
    }

    override fun listCredentials(appId: String): List<CredentialSet> {
        logger.debug("list  credential appId : {} ", appId)
        val account = accountRepository.findOneByAppId(appId) ?: run {
            logger.warn("update account [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }
        return account.credentials
    }

    override fun deleteCredential(appId: String, accessKey: String): List<CredentialSet> {
        logger.info("delete  credential appId : [$appId] , accessKey: [$accessKey]")
        val account = accountRepository.findOneByAppId(appId) ?: run {
            logger.warn("appId [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        // 每种认证授权类型至少存在一个credential
        val deleteCredential = account.credentials.find { it.accessKey == accessKey }
            ?: return transferAccount(account).credentials
        val credentialNum = account.credentials.filter {
            it.authorizationGrantType == deleteCredential.authorizationGrantType
        }.size
        if (credentialNum == 1) {
            throw ErrorCodeException(AuthMessageCode.AUTH_CREDENTIAL_AT_LEAST_ONE)
        }

        account.credentials = account.credentials.filter { it.accessKey != accessKey }
        account.lastModifiedDate = LocalDateTime.now()
        accountRepository.save(account)
        return transferCredentials(account.credentials)
    }

    override fun updateCredentialStatus(appId: String, accessKey: String, status: CredentialStatus): Boolean {
        logger.info("update  credential status appId : [$appId] , accessKey: [$accessKey],status :[$status]")
        accountRepository.findOneByAppId(appId) ?: run {
            logger.warn("update account status  [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }
        val accountQuery = Query.query(
            Criteria.where(TAccount::appId.name).`is`(appId)
                .and("credentials.accessKey").`is`(accessKey)
        )
        val accountResult = mongoTemplate.findOne(accountQuery, TAccount::class.java)
        accountResult?.let {
            val query = Query.query(
                Criteria.where(TAccount::appId.name).`is`(appId)
                    .and("credentials.accessKey").`is`(accessKey)
            )
            val update = Update()
            update.set("credentials.$.status", status.toString())
            val result = mongoTemplate.updateFirst(query, update, TAccount::class.java)
            if (result.modifiedCount == 1L) return true
        }
        return false
    }

    override fun checkCredential(accessKey: String, secretKey: String): String? {
        logger.debug("check  credential  accessKey : [$accessKey] , secretKey: []")
        val query = Query.query(
            Criteria.where("credentials.secretKey").`is`(secretKey)
                .and("credentials.accessKey").`is`(accessKey)
        )
        val result = mongoTemplate.findOne(query, TAccount::class.java) ?: return null
        return result.appId
    }

    private fun transferAccount(tAccount: TAccount, displaySecretKey: Boolean = false): Account {
        if (displaySecretKey) {
            tAccount.credentials = transferCredentials(tAccount.credentials)
        }
        return Account(
            id = tAccount.id!!,
            appId = tAccount.appId,
            locked = tAccount.locked,
            credentials = tAccount.credentials,
            owner = tAccount.owner,
            authorizationGrantTypes = tAccount.authorizationGrantTypes ?: setOf(AuthorizationGrantType.PLATFORM),
            homepageUrl = tAccount.homepageUrl,
            redirectUri = tAccount.redirectUri,
            avatarUrl = tAccount.avatarUrl,
            scope = tAccount.scope,
            description = tAccount.description,
            createdDate = tAccount.createdDate,
            lastModifiedDate = tAccount.lastModifiedDate
        )
    }

    private fun transferCredentials(credentials: List<CredentialSet>): List<CredentialSet> {
        credentials.forEach {
            it.secretKey.replaceRange(6, it.secretKey.length - 1, StringPool.POUND)
        }
        return credentials
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AccountServiceImpl::class.java)
    }
}
