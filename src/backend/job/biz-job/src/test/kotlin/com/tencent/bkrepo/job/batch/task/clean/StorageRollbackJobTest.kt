/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.manager.NodeResourceFactoryImpl
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileExpireResolver
import com.tencent.bkrepo.fs.server.api.FsNodeClient
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_USER
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.config.properties.StorageRollbackJobProperties
import com.tencent.bkrepo.replication.api.ClusterNodeClient
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.api.StoreRecordClient
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.pojo.file.FileReference
import com.tencent.bkrepo.repository.pojo.file.StoreRecord
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.devops.plugin.api.PluginManager
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.util.ReflectionUtils
import java.io.File
import java.time.LocalDateTime

@DisplayName("存储回滚任务测试")
@DataMongoTest
@Import(StorageManager::class, NodeResourceFactoryImpl::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageRollbackJobTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val storageManager: StorageManager,
    private val storageRollbackJob: StorageRollbackJob,
    private val storageRollbackJobProperties: StorageRollbackJobProperties,
) : JobBaseTest() {
    @MockBean
    lateinit var expiredFileResolver: FileExpireResolver

    @MockBean
    private lateinit var fileReferenceClient: FileReferenceClient

    @MockBean
    private lateinit var nodeClient: NodeClient

    @MockBean
    private lateinit var storeRecordClient: StoreRecordClient

    @MockBean
    private lateinit var pluginManager: PluginManager

    @MockBean
    private lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean
    private lateinit var fsNodeClient: FsNodeClient

    @MockBean
    private lateinit var clusterNodeClient: ClusterNodeClient

    @MockBean
    private lateinit var archiveClient: ArchiveClient

    @BeforeEach
    fun beforeEach() {
        mock()
    }

    @AfterEach
    fun afterEach() {
        mongoTemplate.remove(Query(), COLLECTION_NAME)
        mongoTemplate.remove(Query(), "shed_lock")
        for (i in 0 until SHARDING_COUNT) {
            mongoTemplate.remove(Query(), "file_reference_$i")
        }
    }

    @Test
    fun testStoreSuccess() {
        // store file
        assertEquals(0, mongoTemplate.find(Query(), StoreRecord::class.java, COLLECTION_NAME).size)
        store(createTempArtifactFile())
        assertEquals(0, mongoTemplate.find(Query(), StoreRecord::class.java, COLLECTION_NAME).size)
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
        val sha256 = assertStoreFailed()

        // rollback success
        assertRollbackSuccess(sha256)

        // reset mock
        field.set(storageManager, oldStorageService)
    }

    @Test
    fun `test create node failed`() {
        // mock
        whenever(nodeClient.createNode(any())).then { throw RuntimeException() }

        // store failed
        val sha256 = assertStoreFailed()

        // rollback success
        assertRollbackSuccess(sha256, true)
    }

    @Test
    fun `test create node timeout but success`() {
        // mock
        whenever(nodeClient.createNode(any())).then {
            fileReferenceClient.create(it.getArgument<NodeCreateRequest>(0).sha256!!, null, 1L)
            throw RuntimeException()
        }

        // store failed
        val sha256 = assertStoreFailed()

        // rollback success
        assertRollbackSuccess(sha256, true)
    }

    /**
     * 存储失败，并断言存在storeRecord
     */
    private fun assertStoreFailed(): String {
        val artifactFile = createTempArtifactFile()
        assertThrows<RuntimeException> { store(artifactFile) }
        val query = Query(StoreRecord::sha256.isEqualTo(artifactFile.getFileSha256()))
        assertNotNull(mongoTemplate.find(query, StoreRecord::class.java, COLLECTION_NAME))
        return artifactFile.getFileSha256()
    }

    /**
     * 回滚成功，断言不存在storeRecord，根据[refExists]断言文件引用是否存在
     */
    private fun assertRollbackSuccess(sha256: String, refExists: Boolean = false) {
        storageRollbackJob.start()
        val query = Query(StoreRecord::sha256.isEqualTo(sha256))
        assertNull(mongoTemplate.findOne(query, StoreRecord::class.java, COLLECTION_NAME))
        assertEquals(refExists, fileReferenceClient.exists(sha256, null).data!!)
    }

    private fun store(artifactFile: ArtifactFile) {
        val nodeCreateRequest = buildNodeCreateRequest(
            "/a/b/c.txt",
            artifactFile.getSize(),
            artifactFile.getFileSha256()
        )
        storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, null)
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

    private fun insertStoreRecord(sha256: String, credentialsKey: String?): StoreRecord {
        val now = LocalDateTime.now().minusDays(2L)
        val record = StoreRecord(
            id = ObjectId.get().toHexString(),
            createdDate = now,
            lastModifiedDate = now,
            sha256,
            credentialsKey,
        )
        return mongoTemplate.insert(record, COLLECTION_NAME)
    }

    private fun mock() {
        whenever(storeRecordClient.recordStoring(anyString(), anyOrNull())).then {
            Response(code = 0, data = insertStoreRecord(it.getArgument(0), it.getArgument(1)))
        }
        whenever(storeRecordClient.storeFinished(anyString())).then {
            mongoTemplate.remove(Query((Criteria.where(ID).isEqualTo(it.getArgument(0)))), COLLECTION_NAME)
            Response(code = 0, data = true)
        }
        whenever(fileReferenceClient.exists(anyString(), anyOrNull())).then {
            val sha256 = it.getArgument<String>(0)
            val sequence = HashShardingUtils.shardingSequenceFor(sha256, SHARDING_COUNT)
            val collectionName = "file_reference_$sequence"
            val exists = mongoTemplate.exists(
                Query(
                    Criteria.where(FileReference::sha256.name).isEqualTo(it.getArgument(0))
                        .and(FileReference::credentialsKey.name).isEqualTo(it.getArgument(1))
                ),
                collectionName
            )
            Response(code = 0, data = exists)
        }
        whenever(fileReferenceClient.create(anyString(), anyOrNull(), any())).then {
            val sha256 = it.getArgument<String>(0)
            val sequence = HashShardingUtils.shardingSequenceFor(sha256, SHARDING_COUNT)
            val collectionName = "file_reference_$sequence"
            Response(
                code = 0,
                data = mongoTemplate.insert(FileReference(sha256, it.getArgument(1), it.getArgument(2)), collectionName)
            )
        }
        whenever(nodeClient.createNode(any())).then {
            val sha256 = it.getArgument<NodeCreateRequest>(0).sha256!!
            fileReferenceClient.create(sha256, null, 1)
            Response(code = 0, data = buildNodeDetail(sha256))
        }
    }

    companion object {
        private const val COLLECTION_NAME = "store_record"
    }
}
