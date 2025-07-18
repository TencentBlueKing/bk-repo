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

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.metadata.dao.repo.StorageCredentialsDao
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.UT_REGION
import com.tencent.bkrepo.repository.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsCreateRequest
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsUpdateRequest
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration

@Import(NodeDao::class)
@DisplayName("存储身份凭证服务测试")
@DataMongoTest
internal class StorageCredentialServiceTest @Autowired constructor(
    private val storageCredentialService: StorageCredentialService,
    private val storageCredentialsDao: StorageCredentialsDao,
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
) : ServiceBaseTest() {

    @BeforeEach
    fun beforeEach() {
        storageCredentialsDao.remove(Query())
    }

    @Test
    fun testCreate() {
        val credential = createCredential(type = FileSystemCredentials.type) as FileSystemCredentials
        val dbCredentials = storageCredentialService.findByKey(UT_STORAGE_CREDENTIALS_KEY)
        Assertions.assertNotNull(dbCredentials)
        Assertions.assertTrue(dbCredentials is FileSystemCredentials)
        dbCredentials as FileSystemCredentials
        assertEquals(credential.path, dbCredentials.path)
        assertEquals(credential.cache.enabled, dbCredentials.cache.enabled)
        assertEquals(credential.cache.path, dbCredentials.cache.path)
        assertEquals(credential.cache.expireDuration, dbCredentials.cache.expireDuration)

        assertThrows<ErrorCodeException> {
            createCredential()
        }

        assertThrows<ErrorCodeException> {
            val createRequest1 = StorageCredentialsCreateRequest("   ", credential, UT_REGION)
            storageCredentialService.create(UT_USER, createRequest1)
        }
    }

    @Test
    fun testCreateDifferentTypeCredential() {
        val credential = InnerCosCredentials()
        val createRequest = StorageCredentialsCreateRequest(UT_STORAGE_CREDENTIALS_KEY, credential, UT_REGION)
        assertThrows<ErrorCodeException> {
            storageCredentialService.create(UT_USER, createRequest)
        }
    }

    @Test
    fun testUpdateCredential() {
        val storageCredentials = createCredential()
        assertEquals(true, storageCredentials.cache.loadCacheFirst)
        assertEquals(Duration.ofHours(10), storageCredentials.cache.expireDuration)
        assertEquals(UT_STORAGE_CREDENTIALS_KEY, storageCredentials.key)

        var updateCredentialsPayload = storageCredentials.apply {
            cache = cache.copy(loadCacheFirst = false, expireDuration = Duration.ZERO)
        }
        var updateReq = StorageCredentialsUpdateRequest(updateCredentialsPayload, UT_STORAGE_CREDENTIALS_KEY)
        var updatedStorageCredentials = storageCredentialService.update(UT_USER, updateReq)
        assertEquals(false, updatedStorageCredentials.cache.loadCacheFirst)
        assertEquals(Duration.ZERO, updatedStorageCredentials.cache.expireDuration)
        assertEquals(storageCredentials.upload.localPath, updatedStorageCredentials.upload.localPath)
        assertEquals(UT_STORAGE_CREDENTIALS_KEY, updatedStorageCredentials.key)

        val localPath = "/test"
        updateCredentialsPayload = storageCredentials.apply {
            cache = cache.copy(loadCacheFirst = true, expireDuration = Duration.ofHours(10))
            upload = upload.copy(localPath = localPath)
        }
        updateReq = StorageCredentialsUpdateRequest(updateCredentialsPayload, UT_STORAGE_CREDENTIALS_KEY)
        updatedStorageCredentials = storageCredentialService.update(UT_USER, updateReq)
        assertEquals(localPath, updatedStorageCredentials.upload.localPath)
        assertEquals(true, updatedStorageCredentials.cache.loadCacheFirst)
        assertEquals(Duration.ofHours(10), updatedStorageCredentials.cache.expireDuration)
        assertEquals(localPath, updatedStorageCredentials.upload.localPath)
        assertEquals(UT_STORAGE_CREDENTIALS_KEY, updatedStorageCredentials.key)
    }

    @Test
    fun testDelete() {
        // 仅存在一个存储凭证时删除失败
        val credential = createCredential(key = "test")
        assertThrows<BadRequestException> {
            storageCredentialService.delete(credential.key!!)
        }

        // 存在两个及以上存储凭证时删除成功
        createCredential()
        storageCredentialService.delete(credential.key!!)
        assertEquals(null, storageCredentialService.findByKey(credential.key!!))
    }

    @Test
    fun testDeleteNotExistsCredential() {
        assertThrows<NotFoundException> {
            storageCredentialService.delete("KeyOfNotExistsCredential")
        }
    }

    @Test
    fun testDeleteUsedCredential() {
        val credential = createCredential()
        initRepoForUnitTest(projectService, repositoryService, credential.key!!)
        assertThrows<BadRequestException> {
            storageCredentialService.delete(credential.key!!)
        }
    }

    @Test
    fun testList() {
        var list = storageCredentialService.list()
        assertEquals(0, list.size)

        createCredential()
        list = storageCredentialService.list()
        assertEquals(1, list.size)

        createCredential("${UT_STORAGE_CREDENTIALS_KEY}2")
        list = storageCredentialService.list()
        assertEquals(2, list.size)

        createCredential("${UT_STORAGE_CREDENTIALS_KEY}3", "${UT_REGION}2")
        list = storageCredentialService.list()
        assertEquals(3, list.size)

        list = storageCredentialService.list(region = UT_REGION)
        assertEquals(2, list.size)

        list = storageCredentialService.list(region = UT_REGION + "2")
        assertEquals(1, list.size)

        storageCredentialService.forceDelete(UT_STORAGE_CREDENTIALS_KEY + "2")

        list = storageCredentialService.list(region = UT_REGION)
        assertEquals(1, list.size)
    }

    @Test
    fun testGetStorageKeyMapping() {
        val key1 = "key1"
        val key2 = "key2"
        val key3 = "key3"
        createCredential(key1)
        createCredential(key2, filePath = "test2")
        createCredential(key3)

        val mapping = storageCredentialService.getStorageKeyMapping()
        assertEquals(4, mapping.size)
        assertTrue(mapping[key1]!!.size == 1 && mapping[key1]!!.contains(key3))
        assertTrue(mapping[key2]!!.isEmpty())
        assertTrue(mapping[key3]!!.size == 1 && mapping[key3]!!.contains(key1))
        assertTrue(mapping[DEFAULT_STORAGE_KEY]!!.isEmpty())
    }

    private fun createCredential(
        key: String = UT_STORAGE_CREDENTIALS_KEY,
        region: String = UT_REGION,
        type: String = FileSystemCredentials.type,
        filePath: String = "test"
    ): StorageCredentials {
        val credential = when (type) {
            FileSystemCredentials.type -> {
                FileSystemCredentials().apply {
                    path = filePath
                }
            }
            else -> throw RuntimeException("Unknown credential type: $type")
        }.apply { configCredential(this) }

        val createRequest = StorageCredentialsCreateRequest(key, credential, region)
        return storageCredentialService.create(UT_USER, createRequest)
    }

    private fun configCredential(credential: StorageCredentials) {
        credential.apply {
            cache.enabled = true
            cache.path = "cache-test"
            cache.expireDuration = Duration.ofHours(10)
            cache.loadCacheFirst = true
        }
    }
}
