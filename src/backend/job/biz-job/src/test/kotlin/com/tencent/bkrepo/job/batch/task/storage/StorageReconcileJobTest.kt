package com.tencent.bkrepo.job.batch.task.storage

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.random.Random

@DisplayName("存储对账Job测试")
@DataMongoTest(properties = ["job.file-reference-cleanup.expectedNodes=100"])
class StorageReconcileJobTest @Autowired constructor(
    private val storageReconcileJob: StorageReconcileJob,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
) : JobBaseTest() {

    @MockitoBean
    lateinit var servicePermissionClient: ServicePermissionClient

    @MockitoBean
    lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient

    @MockitoBean
    lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockitoBean
    lateinit var messageSupplier: MessageSupplier

    @MockitoBean
    lateinit var archiveClient: ArchiveClient

    @MockitoBean
    lateinit var storageCredentialService: StorageCredentialService

    @MockitoBean
    lateinit var fileReferenceService: FileReferenceService

    @MockitoBean
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
        // 等待job锁过期
        Thread.sleep(1000L)
        Assertions.assertEquals(10, checked)
    }

    @Test
    @DisplayName("验证仅对不支持Drive类型的存储进行对账")
    fun reconcileSkipDriveStorageTest() {
        // 构造一个支持Drive类型的存储凭据（allowRepoTypes包含DRIVE，notAllowRepoTypes为空）
        val driveCredentials = FileSystemCredentials(
            key = "drive-storage-key",
            allowRepoTypes = setOf(RepositoryType.DRIVE.name),
            notAllowRepoTypes = emptySet(),
            path = cred.path.plus("/drive-storage"),
        )

        // 构造一个不支持Drive类型的存储凭据（notAllowRepoTypes包含DRIVE）
        val nonDriveCredentials = FileSystemCredentials(
            key = "non-drive-storage-key", 
            notAllowRepoTypes = setOf(RepositoryType.DRIVE.name),
            path = cred.path.plus("/non-drive-storage"),
        )

        // storageCredentialService.list返回两个存储凭据
        `when`(storageCredentialService.list(null)).thenReturn(listOf(driveCredentials, nonDriveCredentials))

        // 在非Drive存储中存入文件
        repeat(5) {
            val file = createTempArtifactFile()
            storageService.store(file.getFileSha256(), file, nonDriveCredentials)
        }

        // 在Drive存储中存入文件
        repeat(5) {
            val file = createTempArtifactFile()
            storageService.store(file.getFileSha256(), file, driveCredentials)
        }

        var nonDriveChecked = 0
        var driveChecked = 0
        `when`(fileReferenceService.increment(anyString(), anyString(), anyLong())).then {
            val credKey = it.getArgument<String>(1)
            if (credKey == "non-drive-storage-key") {
                nonDriveChecked++
            } else if (credKey == "drive-storage-key") {
                driveChecked++
            }
            true
        }
        `when`(fileReferenceService.exists(anyString(), anyString())).then {
            false
        }

        // 默认存储的notAllowRepoTypes包含DRIVE，也会被对账
        var defaultChecked = 0
        `when`(fileReferenceService.increment(anyString(), isNull(), anyLong())).then {
            defaultChecked++
            true
        }
        `when`(fileReferenceService.exists(anyString(), isNull())).then {
            false
        }

        storageReconcileJob.start()
        // 等待job锁过期
        Thread.sleep(1000L)
        // 验证非Drive存储被对账（5个文件）
        Assertions.assertEquals(5, nonDriveChecked)
        // 验证Drive存储未被对账（driveCredentials中的文件不应被处理）
        Assertions.assertEquals(0, driveChecked)
    }

    private fun createTempArtifactFile(): ArtifactFile {
        val data = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val tempFile = createTempFile()
        tempFile.writeBytes(data)
        return FileSystemArtifactFile(tempFile)
    }
}
