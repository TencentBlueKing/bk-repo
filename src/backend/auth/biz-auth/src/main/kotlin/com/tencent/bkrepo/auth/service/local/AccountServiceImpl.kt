package com.tencent.bkrepo.auth.service.local

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TAccount
import com.tencent.bkrepo.auth.pojo.*
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import com.tencent.bkrepo.auth.repository.AccountRepository
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.auth.util.StrUtil
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*


@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "local")
class AccountServiceImpl @Autowired constructor(
    private val accountRepository: AccountRepository,
    private val mongoTemplate: MongoTemplate
) : AccountService {

    override fun createAccount(request: CreateAccountRequest): Account? {
        val account = accountRepository.findOneByAppId(request.appId)
        if (account != null) {
            logger.warn("create account [${request.appId}]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_APPID)
        }

        val accessKey = "AK:" + UUID.randomUUID().toString()
        val secretKey = "SK:" + StrUtil.generateNonce(30)
        val credentials = CredentialSet(accessKey = accessKey, secretKey = secretKey, createdAt = LocalDateTime.now(), status = CredentialStatus.ENABLE)
        accountRepository.insert(
            TAccount(
                appId = request.appId,
                locked = request.locked,
                credentials = listOf(credentials)
            )
        )
        val result = accountRepository.findOneByAppId(request.appId) ?: return null
        return transfer(result)
    }

    override fun listAccount(): List<Account> {
        return accountRepository.findAllBy().map { transfer(it) }
    }

    override fun deleteAccount(appId: String): Boolean {
        val result = accountRepository.deleteByAppId(appId)
        if (result == 0L) {
            logger.warn("delete account [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }
        return true
    }


    override fun updateAccountStatus(appId: String, locked: Boolean): Boolean {
        val account = accountRepository.findOneByAppId(appId)
        if (account == null) {
            logger.warn("update account [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }

        val query = Query()
        query.addCriteria(Criteria.where("appId").`is`(appId))
        val update = Update()
        update.set("locked", locked)
        val result = mongoTemplate.updateFirst(query, update, TAccount::class.java)
        if (result.modifiedCount == 1L) {
            return true
        }
        return false
    }

    override fun createCredential(appId: String): List<CredentialSet> {
        val account = accountRepository.findOneByAppId(appId)
        if (account == null) {
            logger.warn("account [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }

        val query = Query.query(Criteria.where("appId").`is`(appId))
        val update = Update()
        val accessKey = "AK:" + UUID.randomUUID().toString()
        val secretKey = "SK:" + StrUtil.generateNonce(30)
        val credentials = CredentialSet(accessKey = accessKey, secretKey = secretKey, createdAt = LocalDateTime.now(), status = CredentialStatus.ENABLE)
        update.addToSet("credentials", credentials)
        mongoTemplate.upsert(query, update, TAccount::class.java)
        val result = accountRepository.findOneByAppId(appId) ?: return emptyList()
        return result.credentials
    }

    override fun listCredentials(appId: String): List<CredentialSet> {
        val account = accountRepository.findOneByAppId(appId)
        if (account == null) {
            logger.warn("update account [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }
        return account.credentials
    }


    override fun deleteCredential(appId: String, accessKey: String): List<CredentialSet> {
        val account = accountRepository.findOneByAppId(appId)
        if (account == null) {
            logger.warn("appId [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        val query = Query.query(Criteria.where("appId").`is`(appId))
        val s = BasicDBObject()
        s["accessKey"] = accessKey
        val update = Update()
        update.pull("credentials", s)
        mongoTemplate.updateFirst(query, update, TAccount::class.java)
        val result = accountRepository.findOneByAppId(appId) ?: return emptyList()
        return result.credentials
    }

    override fun updateCredentialStatus(appId: String, accessKey: String, status: CredentialStatus): Boolean {
        val account = accountRepository.findOneByAppId(appId)
        if (account == null) {
            logger.warn("update account status  [$appId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_APPID_NOT_EXIST)
        }
        val accountQuery = Query.query(Criteria.where("appId").`is`(appId)
            .and("credentials.accessKey").`is`(accessKey))
        val accountResult = mongoTemplate.findOne(accountQuery, TAccount::class.java)
        if (accountResult != null) {
            val query = Query.query(Criteria.where("appId").`is`(appId)
                .and("credentials.accessKey").`is`(accessKey))
            val update = Update()
            update.set("credentials.$.status", status.toString())
            val result = mongoTemplate.updateFirst(query, update, TAccount::class.java)
            if (result.modifiedCount == 1L) {
                return true
            }
        }
        return false
    }

    override fun checkCredential(accessKey: String, secretKey: String): Boolean {
        val query = Query.query(Criteria.where("credentials.secretKey").`is`(secretKey)
            .and("credentials.accessKey").`is`(accessKey))
        mongoTemplate.findOne(query, TAccount::class.java) ?: return false
        return true
    }

    private fun transfer(tAccount: TAccount): Account {
        return Account(
            appId = tAccount.appId,
            locked = tAccount.locked,
            credentials = tAccount.credentials
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AccountServiceImpl::class.java)
    }
}