package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@DisplayName("存储身份凭证服务测试")
@SpringBootTest
internal class StorageCredentialServiceTest @Autowired constructor(
    private val storageCredentialService: StorageCredentialService
) {

    private val storageCredentialsKey = "unit-test-credentials-key"


    @BeforeEach
    fun setUp() {
        storageCredentialService.delete(storageCredentialsKey)
    }

    @AfterEach
    fun tearDown() {
        storageCredentialService.delete(storageCredentialsKey)
    }

    @Test
    fun testCreate() {
        val credential = FileSystemCredentials()
        credential.path = "test"
        credential.cache.enabled = true
        credential.cache.path = "cache-test"
        credential.cache.expireDays = 10

        val createRequest = StorageCredentialsCreateRequest(storageCredentialsKey, credential)
        storageCredentialService.create("system", createRequest)

        val dbCredentials = storageCredentialService.findByKey(storageCredentialsKey)
        Assertions.assertNotNull(dbCredentials)
        Assertions.assertTrue(dbCredentials is FileSystemCredentials)
        dbCredentials as FileSystemCredentials
        Assertions.assertEquals(credential.path, dbCredentials.path)
        Assertions.assertEquals(credential.cache.enabled, dbCredentials.cache.enabled )
        Assertions.assertEquals(credential.cache.path, dbCredentials.cache.path )
        Assertions.assertEquals(credential.cache.expireDays, dbCredentials.cache.expireDays )

        assertThrows<ErrorCodeException>{
            storageCredentialService.create("system", createRequest)
        }


        assertThrows<ErrorCodeException>{
            val createRequest1 = StorageCredentialsCreateRequest("   ", credential)
            storageCredentialService.create("system", createRequest1)
        }
    }

    @Test
    fun testList() {
        var list = storageCredentialService.list()
        Assertions.assertEquals(0, list.size )

        val credential1 = FileSystemCredentials()
        credential1.path = "test"
        credential1.cache.enabled = true
        credential1.cache.path = "cache-test"
        credential1.cache.expireDays = 10

        val createRequest1 = StorageCredentialsCreateRequest(storageCredentialsKey, credential1)
        storageCredentialService.create("system", createRequest1)

        list = storageCredentialService.list()
        Assertions.assertEquals(1, list.size )

        val credential2 = InnerCosCredentials()
        credential2.bucket = "test"
        credential2.cache.enabled = true
        credential2.cache.path = "cache-test"
        credential2.cache.expireDays = 10

        val createRequest2 = StorageCredentialsCreateRequest(storageCredentialsKey + "2", credential2)
        storageCredentialService.create("system", createRequest2)

        list = storageCredentialService.list()
        Assertions.assertEquals(2, list.size )

        storageCredentialService.delete(storageCredentialsKey + "2")

        list = storageCredentialService.list()
        Assertions.assertEquals(1, list.size )
    }

}