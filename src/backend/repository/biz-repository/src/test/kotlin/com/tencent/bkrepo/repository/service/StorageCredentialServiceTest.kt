package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.repository.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest

@DisplayName("存储身份凭证服务测试")
@DataMongoTest
internal class StorageCredentialServiceTest @Autowired constructor(
    private val storageCredentialService: StorageCredentialService
) : ServiceBaseTest() {

    @BeforeEach
    fun beforeEach() {
        storageCredentialService.delete(UT_STORAGE_CREDENTIALS_KEY)
    }

    @Test
    fun testCreate() {
        val credential = FileSystemCredentials()
        credential.path = "test"
        credential.cache.enabled = true
        credential.cache.path = "cache-test"
        credential.cache.expireDays = 10

        val createRequest = StorageCredentialsCreateRequest(UT_STORAGE_CREDENTIALS_KEY, credential)
        storageCredentialService.create(UT_USER, createRequest)

        val dbCredentials = storageCredentialService.findByKey(UT_STORAGE_CREDENTIALS_KEY)
        Assertions.assertNotNull(dbCredentials)
        Assertions.assertTrue(dbCredentials is FileSystemCredentials)
        dbCredentials as FileSystemCredentials
        Assertions.assertEquals(credential.path, dbCredentials.path)
        Assertions.assertEquals(credential.cache.enabled, dbCredentials.cache.enabled)
        Assertions.assertEquals(credential.cache.path, dbCredentials.cache.path)
        Assertions.assertEquals(credential.cache.expireDays, dbCredentials.cache.expireDays)

        assertThrows<ErrorCodeException> {
            storageCredentialService.create(UT_USER, createRequest)
        }

        assertThrows<ErrorCodeException> {
            val createRequest1 = StorageCredentialsCreateRequest("   ", credential)
            storageCredentialService.create(UT_USER, createRequest1)
        }
    }

    @Test
    fun testCreateDifferentTypeCredential() {
        val credential = InnerCosCredentials()
        val createRequest = StorageCredentialsCreateRequest(UT_STORAGE_CREDENTIALS_KEY, credential)
        assertThrows<ErrorCodeException> {
            storageCredentialService.create(UT_USER, createRequest)
        }
    }

    @Test
    fun testList() {
        var list = storageCredentialService.list()
        Assertions.assertEquals(0, list.size)

        val credential1 = FileSystemCredentials()
        credential1.path = "test"
        credential1.cache.enabled = true
        credential1.cache.path = "cache-test"
        credential1.cache.expireDays = 10

        val createRequest1 = StorageCredentialsCreateRequest(UT_STORAGE_CREDENTIALS_KEY, credential1)
        storageCredentialService.create(UT_USER, createRequest1)

        list = storageCredentialService.list()
        Assertions.assertEquals(1, list.size)

        val credential2 = FileSystemCredentials()
        credential1.path = "test2"
        credential1.cache.enabled = true
        credential1.cache.path = "cache-test2"
        credential1.cache.expireDays = 10

        val createRequest2 = StorageCredentialsCreateRequest(UT_STORAGE_CREDENTIALS_KEY + "2", credential2)
        storageCredentialService.create(UT_USER, createRequest2)

        list = storageCredentialService.list()
        Assertions.assertEquals(2, list.size)

        storageCredentialService.delete(UT_STORAGE_CREDENTIALS_KEY + "2")

        list = storageCredentialService.list()
        Assertions.assertEquals(1, list.size)
    }
}
