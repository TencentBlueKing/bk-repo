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
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.FileReferenceCleanupJobProperties
import com.tencent.bkrepo.job.repository.JobSnapshotRepository
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.cloud.sleuth.Tracer
import org.springframework.cloud.sleuth.otel.bridge.OtelTracer
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.isEqualTo

@DisplayName("文件引用清理Job测试")
@DataMongoTest
class FileReferenceCleanupJobTest : JobBaseTest() {

    @MockBean
    lateinit var storageService: StorageService

    @MockBean
    lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean
    lateinit var archiveClient: ArchiveClient

    @MockBean
    lateinit var repositoryClient: RepositoryClient

    @MockBean
    lateinit var jobSnapshotRepository: JobSnapshotRepository

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var fileReferenceCleanupJob: FileReferenceCleanupJob

    @Autowired
    lateinit var nodeCommonUtils: NodeCommonUtils

    @Autowired
    lateinit var repositoryCommonUtils: RepositoryCommonUtils

    @Autowired
    lateinit var fileReferenceCleanupJobProperties: FileReferenceCleanupJobProperties

    @MockBean
    lateinit var fileReferenceClient: FileReferenceClient

    @BeforeEach
    fun beforeEach() {
        val tracer = mockk<OtelTracer>()
        mockkObject(SpringContextUtils.Companion)
        every { SpringContextUtils.getBean<Tracer>() } returns tracer
        every { tracer.currentSpan() } returns null
        every { SpringContextUtils.publishEvent(any()) } returns Unit
        Mockito.`when`(storageService.exist(anyString(), any())).thenReturn(true)
        val credentials = InnerCosCredentials()
        Mockito.`when`(storageCredentialsClient.findByKey(anyString())).thenReturn(
            ResponseBuilder.success(credentials),
        )
        Mockito.`when`(repositoryClient.getRepoDetail(anyString(), anyString(), anyString())).thenReturn(
            ResponseBuilder.success(
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
                ),
            ),
        )
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
