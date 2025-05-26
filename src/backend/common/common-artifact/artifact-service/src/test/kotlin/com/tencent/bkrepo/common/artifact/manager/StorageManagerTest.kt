package com.tencent.bkrepo.common.artifact.manager

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.util.Constant.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.util.Constant.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.util.Constant.UT_SHA256
import com.tencent.bkrepo.common.artifact.util.Constant.UT_USER
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import com.tencent.bkrepo.common.metadata.pojo.node.NodeInfo
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCreateRequest
import com.tencent.devops.plugin.api.PluginManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.util.ReflectionUtils
import java.io.File

@DisplayName("存储管理器测试")
@DataMongoTest
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(StorageManager::class)
@TestPropertySource(
    locations = ["classpath:bootstrap-ut.properties"]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageManagerTest @Autowired constructor(
    private val storageManager: StorageManager,
) {
    @MockBean
    private lateinit var fileReferenceService: FileReferenceService

    @MockBean
    private lateinit var nodeService: NodeService

    @MockBean
    private lateinit var pluginManager: PluginManager

    @MockBean
    private lateinit var storageService: StorageService

    @MockBean
    private lateinit var nodeResourceFactory: NodeResourceFactory

    @BeforeEach
    fun beforeEach() {
        whenever(storageService.store(anyString(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(1)
        whenever(nodeService.createNode(any()))
            .thenReturn(buildNodeDetail(UT_SHA256))
        whenever(fileReferenceService.increment(anyString(), anyOrNull(), any()))
            .thenReturn(true)
    }

    @Test
    fun testStoreSuccess() {
        store()
        verify(nodeService, times(1)).createNode(any())
        verify(fileReferenceService, times(0)).increment(anyString(), anyOrNull(), any())
    }

    @Test
    fun `test store failed`() {
        // mock
        val storageService = mock<StorageService>()
        whenever(storageService.store(anyString(), any(), anyOrNull(), anyOrNull())).then { throw RuntimeException() }
        val field = ReflectionUtils.findField(StorageManager::class.java, "storageService")!!
        field.isAccessible = true
        val oldStorageService = field.get(storageManager)
        field.set(storageManager, storageService)

        // store failed
        assertThrows<RuntimeException> { store() }
        verify(nodeService, times(0)).createNode(any())
        verify(fileReferenceService, times(0)).increment(anyString(), anyOrNull(), any())

        // reset mock
        field.set(storageManager, oldStorageService)
    }

    @Test
    fun `test create node failed`() {
        // mock
        whenever(nodeService.createNode(any())).then { throw RuntimeException() }

        // store failed
        assertThrows<RuntimeException> { store() }
        verify(nodeService, times(1)).createNode(any())
        verify(fileReferenceService, times(1)).increment(anyString(), anyOrNull(), any())
    }

    private fun store(): String {
        val file = createTempArtifactFile()
        val sha256 = file.getFileSha256()
        val req = buildNodeCreateRequest("/a/b/c.txt", 10240L, sha256)
        try {
            storageManager.storeArtifactFile(req, file, null)
        } finally {
            file.delete()
        }
        return sha256
    }

    private fun createTempArtifactFile(size: Long = 10240L): ArtifactFile {
        val tempFile = File.createTempFile("tmp", "")
        val content = StringPool.randomString(size.toInt())
        content.byteInputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return FileSystemArtifactFile(tempFile)
    }

    private fun buildNodeCreateRequest(fullPath: String, size: Long, sha256: String) = NodeCreateRequest(
        projectId = UT_PROJECT_ID,
        repoName = UT_REPO_NAME,
        folder = false,
        fullPath = fullPath,
        expires = 0,
        overwrite = false,
        size = size,
        sha256 = sha256,
        md5 = "md5",
    )

    private fun buildNodeDetail(sha256: String): NodeDetail {
        val nodeInfo = NodeInfo(
            createdBy = UT_USER,
            createdDate = "",
            lastModifiedBy = UT_USER,
            lastModifiedDate = "",
            folder = false,
            sha256 = sha256,
            path = "/a/b",
            name = "c.txt",
            fullPath = "/a/b/c.txt",
            size = 10240L,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME
        )
        return NodeDetail(nodeInfo)
    }
}
