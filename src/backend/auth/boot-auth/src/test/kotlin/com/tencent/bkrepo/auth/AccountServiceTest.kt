package com.tencent.bkrepo.auth

import com.tencent.bkrepo.auth.constant.RANDOM_KEY_LENGTH
import com.tencent.bkrepo.auth.pojo.CreateAccountRequest
import com.tencent.bkrepo.auth.pojo.CredentialSet
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import com.tencent.bkrepo.auth.service.AccountService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("服务账号相关接口")
class AccountServiceTest {

    @Autowired
    private lateinit var accountService: AccountService

    private val APP_ID = "unit_test_id"

    @BeforeEach
    fun setUp() {
        accountService.listAccount().filter { APP_ID.equals(it.appId) }.forEach {
            accountService.deleteAccount(APP_ID)
        }
    }

    @AfterEach
    fun teardown() {
        accountService.listAccount().filter { APP_ID.equals(it.appId) }.forEach {
            accountService.deleteAccount(APP_ID)
        }
    }

    @Test
    @DisplayName("创建账户测试")
    fun createAccountTest() {
        val account = accountService.createAccount(buildCreateAccountRequest())!!
        assertThrows<ErrorCodeException> { accountService.createAccount(buildCreateAccountRequest()) }
        Assertions.assertEquals(account.appId, APP_ID)
        Assertions.assertFalse(account.locked)
    }

    @Test
    @DisplayName("查询账户测试")
    fun listAccountTest() {
        accountService.createAccount(buildCreateAccountRequest())
        accountService.createAccount(buildCreateAccountRequest(appId = "test1"))
        accountService.createAccount(buildCreateAccountRequest(appId = "test2", locked = true))
        Assertions.assertTrue(accountService.listAccount().size == 3)
        accountService.deleteAccount("test1")
        accountService.deleteAccount("test2")
    }

    @Test
    @DisplayName("删除账户测试")
    fun deleteAccountTest() {
        accountService.createAccount(buildCreateAccountRequest())
        accountService.deleteAccount(APP_ID)
        assertThrows<ErrorCodeException> { accountService.deleteAccount(APP_ID) }
    }

    @Test
    @DisplayName("修改账户状态测试")
    fun updateAccountStatusTest() {
        assertThrows<ErrorCodeException> { accountService.updateAccountStatus(APP_ID, true) }
        accountService.createAccount(buildCreateAccountRequest())
        val updateAccountStatus = accountService.updateAccountStatus(APP_ID, true)
        Assertions.assertTrue(updateAccountStatus)
    }

    @Test
    @DisplayName("创建ak/sk对测试")
    fun createCredentialTest() {
        assertThrows<ErrorCodeException> { accountService.createCredential(APP_ID) }
        // 创建用户会自带创建一个as/sk对
        accountService.createAccount(buildCreateAccountRequest())
        val createCredential = accountService.createCredential(APP_ID)
        Assertions.assertTrue(createCredential.size == 2)
        with(createCredential[1]) {
            Assertions.assertTrue(this.accessKey.length == 32)
            Assertions.assertTrue(this.secretKey.length == RANDOM_KEY_LENGTH)
        }
    }

    @Test
    @DisplayName("获取as/sk对测试")
    fun listCredentialsTest() {
        accountService.createAccount(buildCreateAccountRequest())
        val credentialsList = accountService.listCredentials(APP_ID)
        Assertions.assertTrue(credentialsList.size == 1)
    }

    @Test
    @DisplayName("获取as/sk对测试")
    fun deleteCredentialTest() {
        val account = accountService.createAccount(buildCreateAccountRequest())!!
        val credentialsList = accountService.deleteCredential(account.appId, account.credentials[0].accessKey)
        Assertions.assertTrue(credentialsList.isEmpty())
    }

    @Test
    @DisplayName("更新ak/sk对状态测试")
    fun updateCredentialStatusTest() {
        val account = accountService.createAccount(buildCreateAccountRequest())!!
        val accessKey = account.credentials[0].accessKey
        val credentialStatus = account.credentials[0].status
        Assertions.assertEquals(credentialStatus, CredentialStatus.ENABLE)
        val status = accountService.updateCredentialStatus(account.appId, accessKey, CredentialStatus.DISABLE)
        Assertions.assertTrue(status)
    }

    @Test
    @DisplayName("校验ak/sk")
    fun checkCredentialTest() {
        val account = accountService.createAccount(buildCreateAccountRequest())!!
        val accessKey = account.credentials[0].accessKey
        val secretKey = account.credentials[0].secretKey
        val checkCredential = accountService.checkCredential("accessKey", "secretKey")
        Assertions.assertNull(checkCredential)
        val credential = accountService.checkCredential(accessKey, secretKey)
        credential?.let { Assertions.assertEquals(APP_ID, it) }
    }

    private fun buildCreateAccountRequest(appId: String = APP_ID, locked: Boolean = false): CreateAccountRequest {
        return CreateAccountRequest(appId, locked)
    }
}