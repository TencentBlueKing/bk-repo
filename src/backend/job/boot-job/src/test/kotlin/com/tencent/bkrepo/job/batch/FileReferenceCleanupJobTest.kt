package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.bson.Document
import org.junit.jupiter.api.*
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("文件引用清理Job测试")
@DataMongoTest
@Import(
    FileReferenceCleanupJob::class
)
class FileReferenceCleanupJobTest : JobBaseTest() {

    @MockBean
    lateinit var storageService: StorageService

    @MockBean
    lateinit var storageCredentialsClient: StorageCredentialsClient

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var fileReferenceCleanupJob: FileReferenceCleanupJob

    @BeforeEach
    fun beforeEach() {
        Mockito.`when`(storageService.exist(anyString(), any())).thenReturn(true)
        val credentials = InnerCosCredentials()
        Mockito.`when`(storageCredentialsClient.findByKey(anyString())).thenReturn(
            ResponseBuilder.success(credentials)
        )
    }

    @AfterEach
    fun afterEach() {
        fileReferenceCleanupJob.collectionNames().forEach {
            mongoTemplate.remove(Query(), it)
        }
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

    @DisplayName("测试单表大数据,分页删除")
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

    private fun insertMany(num: Int, collectionName: String) {
        (0 until num).forEach {
            val doc = Document(
                mutableMapOf(
                    "sha256" to it.toString(),
                    "credentialsKey" to it.toString(),
                    "count" to 0
                ) as Map<String, Any>?
            )
            mongoTemplate.insert(
                doc,
                collectionName
            )
        }
    }
}
