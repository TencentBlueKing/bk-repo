package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.BaseTest
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.constant.MAX_CHAIN_LENGTH
import com.tencent.bkrepo.archive.job.compress.BDZipManager
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean

@DataMongoTest
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
class CompressServiceTest @Autowired constructor(
    private val compressService: CompressService,
    private val compressFileRepository: CompressFileRepository,
) : BaseTest() {
    @MockBean
    lateinit var bdZipManager: BDZipManager

    @MockBean
    lateinit var storageService: StorageService

    @MockBean
    lateinit var fileReferenceClient: FileReferenceClient

    @MockBean
    lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean
    lateinit var repositoryClient: RepositoryClient

    @BeforeEach
    fun beforeEach() {
        compressFileRepository.deleteAll()
        initMock()
    }

    @Test
    fun compressTest() {
        with(createCompressFile()) {
            val create = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, null)
            Assertions.assertEquals(CompressStatus.CREATED, create!!.status)
        }
    }

    @Test
    fun maxChainLengthTest() {
        for (i in 0..MAX_CHAIN_LENGTH) {
            if (i == MAX_CHAIN_LENGTH) {
                assertThrows<ErrorCodeException> { createCompressFile("$i", "${i + 1}") }
            } else {
                createCompressFile("$i", "${i + 1}")
            }
        }
    }

    @Test
    fun compressFailedTest() {
        val f1 = "f1"
        val f2 = "f2"
        createCompressFile(f1, f2)
        // base已经压缩
        assertThrows<ErrorCodeException> { createCompressFile(f2, f1) }
        // 相同的sha256
        assertThrows<IllegalArgumentException> { createCompressFile(f1, f1) }
    }

    private fun createCompressFile(
        sha256: String = StringPool.randomString(64),
        baseSha256: String = StringPool.randomString(64),
    ): CompressFileRequest {
        val request = CompressFileRequest(
            sha256 = sha256,
            size = 1,
            baseSha256 = baseSha256,
            baseSize = 1,
            storageCredentialsKey = null,
            operator = "ut-op",
        )
        compressService.compress(request)
        return request
    }
}
