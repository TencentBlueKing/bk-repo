package com.tencent.bkrepo.job.batch.task.storage

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.isNull
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import kotlin.random.Random

@DisplayName("存储对账Job测试")
@DataMongoTest
class StorageReconcileJobTest @Autowired constructor(
    private val storageReconcileJob: StorageReconcileJob,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
) : JobBaseTest() {

    @MockBean
    lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockBean
    lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean
    lateinit var fileReferenceClient: FileReferenceClient

    @MockBean
    lateinit var repositoryClient: RepositoryClient

    @Autowired
    lateinit var nodeCommonUtils: NodeCommonUtils
    private val cred = storageProperties.defaultStorageCredentials() as FileSystemCredentials

    init {
        cred.path = System.getProperty("java.io.tmpdir").plus("/ut-test")
    }

    @BeforeEach
    fun before() {
        `when`(storageCredentialsClient.list(null)).thenReturn(ResponseBuilder.success(emptyList()))
    }

    @AfterEach
    fun afterEach() {
        cred.path.toPath().toFile().deleteRecursively()
    }

    @Test
    fun reconcileTest() {
        var checked = 0
        `when`(fileReferenceClient.increment(anyString(), isNull(), anyLong())).then {
            checked++
            ResponseBuilder.success(true)
        }
        repeat(10) {
            val file = createTempArtifactFile()
            storageService.store(file.getFileSha256(), file, null)
        }
        storageReconcileJob.start()
        Assertions.assertEquals(10, checked)
    }

    private fun createTempArtifactFile(): ArtifactFile {
        val data = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val tempFile = createTempFile()
        tempFile.writeBytes(data)
        return FileSystemArtifactFile(tempFile)
    }
}
