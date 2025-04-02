/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.constant.DEFAULT_KEY
import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.CREDENTIALS
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.task.clean.FileReferenceCleanupJob
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.FileReferenceCleanupJobProperties
import com.tencent.bkrepo.job.repository.JobSnapshotRepository
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.router.api.RouterControllerClient
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("文件引用清理Job测试")
@DataMongoTest
class FileReferenceCleanupJobTest : JobBaseTest() {

    @MockBean
    lateinit var storageService: StorageService

    @MockBean
    lateinit var storageCredentialService: StorageCredentialService

    @MockBean
    lateinit var archiveClient: ArchiveClient

    @MockBean
    lateinit var repositoryService: RepositoryService

    @MockBean
    private lateinit var messageSupplier: MessageSupplier

    @MockBean
    private lateinit var servicePermissionClient: ServicePermissionClient

    @MockBean
    private lateinit var routerControllerClient: RouterControllerClient

    @MockBean
    private lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient

    @MockBean
    lateinit var jobSnapshotRepository: JobSnapshotRepository

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var redisTemplate: RedisTemplate<String, String>

    @Autowired
    lateinit var fileReferenceCleanupJob: FileReferenceCleanupJob

    @Autowired
    lateinit var nodeCommonUtils: NodeCommonUtils

    @Autowired
    lateinit var repositoryCommonUtils: RepositoryCommonUtils

    @Autowired
    lateinit var fileReferenceCleanupJobProperties: FileReferenceCleanupJobProperties

    @MockBean
    lateinit var operateLogService: OperateLogService

    @BeforeEach
    fun beforeEach() {
        Mockito.`when`(storageService.exist(anyString(), any())).thenReturn(true)
        val credentials = InnerCosCredentials()
        Mockito.`when`(storageCredentialService.findByKey(anyString())).thenReturn(
            credentials
        )
        Mockito.`when`(repositoryService.getRepoDetail(anyString(), anyString(), anyString())).thenReturn(
            RepositoryDetail(
                projectId = "ut-project",
                name = "ut-repo",
                storageCredentials = InnerCosCredentials(key = "0"),
                type = RepositoryType.NONE,
                category = RepositoryCategory.LOCAL,
                public = false,
                description = "",
                configuration = LocalConfiguration(),
                createdBy = "",
                createdDate = "",
                lastModifiedBy = "",
                lastModifiedDate = "",
                oldCredentialsKey = null,
                quota = 0,
                used = 0,
            )
        )
        RepositoryCommonUtils.updateService(repositoryService, storageCredentialService)
        fileReferenceCleanupJobProperties.expectedNodes = 50_000
    }

    @AfterEach
    fun afterEach() {
        fileReferenceCleanupJob.collectionNames().forEach {
            mongoTemplate.remove(Query(), it)
        }
        mongoTemplate.remove(Query(), "node_0")
        mongoTemplate.remove(Query(), "shed_lock")
    }

    @DisplayName("测试正常运行")
    @Test
    fun run() {
        val num = 100
        fileReferenceCleanupJob.collectionNames().forEach {
            insertMany(num, it)
        }
        val deleted = AtomicInteger()
        Mockito.`when`(storageService.delete(anyString(), any())).then { deleted.incrementAndGet() }
        fileReferenceCleanupJob.start()
        Assertions.assertEquals(SHARDING_COUNT * num, deleted.get())
    }

    @DisplayName("测试排它执行")
    @Test
    fun exclusiveTest() {
        Assertions.assertTrue(fileReferenceCleanupJob.start())
        Assertions.assertFalse(fileReferenceCleanupJob.start())
    }

    @DisplayName("测试单表大数据,分页清理")
    @Test
    fun bigCollection() {
        val num = 50_000
        insertMany(num, fileReferenceCleanupJob.collectionNames().first())
        val deleted = AtomicInteger()
        Mockito.`when`(storageService.delete(anyString(), any())).then {
            deleted.incrementAndGet()
        }
        fileReferenceCleanupJob.start()
        Assertions.assertEquals(num, deleted.get())
    }

    @DisplayName("测试错误引用数据清理")
    @Test
    fun errorRefTest() {
        val num = 1000
        val collectionName = fileReferenceCleanupJob.collectionNames().first()
        insertMany(num, collectionName)
        // 新增一个节点，制造引用错误
        val doc = Document(
            mutableMapOf(
                "sha256" to "0",
                "folder" to false,
                "projectId" to "ut-project",
                "repoName" to "ut-repo",
                "size" to 1L,
                "fullPath" to "/test",
            ) as Map<String, Any>?,
        )
        mongoTemplate.insert(
            doc,
            "node_0",
        )
        val deleted = AtomicInteger()
        Mockito.`when`(storageService.delete(anyString(), any())).then {
            deleted.incrementAndGet()
        }
        fileReferenceCleanupJob.start()
        Assertions.assertEquals(num - 1, deleted.get())
        val query = Query.query(Criteria.where("sha256").isEqualTo("0"))
        val find = mongoTemplate.findOne<Map<String, Any?>>(query, collectionName)
        Assertions.assertEquals(1L, find?.get("count"))
    }

    @Test
    fun ignoreStorageCredentialsKeysTest() {
        val doc = Document(
            mutableMapOf(
                "sha256" to "0",
                "credentialsKey" to null,
                "count" to 0,
            ) as Map<String, Any>?,
        )
        val doc2 = Document(
            mutableMapOf(
                "sha256" to "1",
                "credentialsKey" to "key1",
                "count" to 0,
            ) as Map<String, Any>?,
        )
        val doc3 = Document(
            mutableMapOf(
                "sha256" to "2",
                "credentialsKey" to "key2",
                "count" to 0,
            ) as Map<String, Any>?,
        )
        val doc4 = Document(
            mutableMapOf(
                "sha256" to "0",
                "count" to 0,
            ) as Map<String, Any>?,
        )
        val collectionName = fileReferenceCleanupJob.collectionNames().first()
        mongoTemplate.insert(listOf(doc, doc2, doc3, doc4), collectionName)
        fileReferenceCleanupJobProperties.ignoredStorageCredentialsKeys = setOf(DEFAULT_KEY, "key1")
        val query = fileReferenceCleanupJob.buildQuery()
        val finds = mongoTemplate.find<Map<String, Any?>>(query, collectionName)
        Assertions.assertEquals(1, finds.size)
        Assertions.assertEquals("key2", finds.first()["credentialsKey"])
    }

    @Test
    fun mappingStorageKeyTest() {
        whenever(storageCredentialService.getStorageKeyMapping()).thenReturn(
            mapOf(
                "key1" to setOf("key2", "key4", DEFAULT_STORAGE_KEY),
                "key2" to setOf("key1", "key4", DEFAULT_STORAGE_KEY),
                "key4" to setOf("key1", "key2", DEFAULT_STORAGE_KEY),
                DEFAULT_STORAGE_KEY to setOf("key1", "key2", "key4"),
            )
        )
        val collectionName = fileReferenceCleanupJob.collectionNames().first()
        val sha2561 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c1"
        val sha2562 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c2"
        val sha2563 = "688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c3"
        insertOne(sha2561, "key1", 0, collectionName)
        insertOne(sha2561, "key2", 1, collectionName)
        insertOne(sha2563, "key3", 0, collectionName)
        insertOne(sha2562, "key4", 0, collectionName)
        insertOne(sha2562, null, 1, collectionName)

        val deleted = HashSet<String>()
        whenever(storageService.delete(anyString(), any())).then {
            deleted.add(it.getArgument(0))
        }
        fileReferenceCleanupJob.start()

        // assert
        assertTrue(deleted.size == 1 && deleted.contains(sha2563))

        assertFalse(existsRef(sha2561, "key1", collectionName))
        assertTrue(existsRef(sha2561, "key2", collectionName))
        assertFalse(existsRef(sha2563, "key3", collectionName))
        assertFalse(existsRef(sha2562, "key4", collectionName))
        assertTrue(existsRef(sha2562, null, collectionName))
    }

    private fun existsRef(sha256: String, key: String?, collectionName: String): Boolean {
        val criteria = Criteria.where(SHA256).isEqualTo(sha256).and(CREDENTIALS).isEqualTo(key)
        return mongoTemplate.exists(Query(criteria), collectionName)
    }

    private fun insertOne(sha256: String, key: String?, count: Int, collectionName: String) {
        mongoTemplate.insert(
            Document(
                mutableMapOf(
                    "sha256" to sha256,
                    "credentialsKey" to key,
                    "count" to count,
                ) as Map<String, Any>?,
            ), collectionName
        )
    }

    private fun insertMany(num: Int, collectionName: String) {
        (0 until num).forEach {
            val doc = Document(
                mutableMapOf(
                    "sha256" to it.toString(),
                    "credentialsKey" to it.toString(),
                    "count" to 0,
                ) as Map<String, Any>?,
            )
            mongoTemplate.insert(
                doc,
                collectionName,
            )
        }
    }
}
