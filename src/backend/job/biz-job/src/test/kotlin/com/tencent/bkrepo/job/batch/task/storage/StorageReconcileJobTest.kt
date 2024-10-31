package com.tencent.bkrepo.job.batch.task.storage

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.router.api.RouterControllerClient
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
@DataMongoTest(properties = ["job.file-reference-cleanup.expectedNodes=100"])
class StorageReconcileJobTest @Autowired constructor(
    private val storageReconcileJob: StorageReconcileJob,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
) : JobBaseTest() {

    @MockBean
    private lateinit var routerControllerClient: RouterControllerClient

    @MockBean
    lateinit var servicePermissionClient: ServicePermissionClient

    @MockBean
    lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient

    @MockBean
    lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockBean
    lateinit var messageSupplier: MessageSupplier

    @MockBean
    lateinit var archiveClient: ArchiveClient

    @MockBean
    lateinit var storageCredentialService: StorageCredentialService

    @MockBean
    lateinit var fileReferenceService: FileReferenceService

    @MockBean
    lateinit var operateLogService: OperateLogService

    @Autowired
    lateinit var nodeCommonUtils: NodeCommonUtils

    private val cred = storageProperties.defaultStorageCredentials() as FileSystemCredentials

    init {
        cred.path = System.getProperty("java.io.tmpdir").plus("/ut-test")
    }

    @BeforeEach
    fun before() {
        `when`(storageCredentialService.list(null)).thenReturn(emptyList())
    }

    @AfterEach
    fun afterEach() {
        cred.path.toPath().toFile().deleteRecursively()
    }

    @Test
    fun reconcileTest() {
        var checked = 0
        `when`(fileReferenceService.increment(anyString(), isNull(), anyLong())).then {
            checked++
            true
        }
        `when`(fileReferenceService.exists(anyString(), isNull())).then {
            false
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
