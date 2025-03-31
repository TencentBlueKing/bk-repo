package com.tencent.bkrepo.job.migrate.strategy

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createArchiveFile
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.insertFailedNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.mockRepositoryCommonUtils
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource

@DisplayName("归档文件迁移失败自动修复策略测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    ArchivedFileAutoFixStrategy::class,
    ArchiveFileDao::class,
    MigrateFailedNodeDao::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class ArchivedFileAutoFixStrategyTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val archiveFileDao: ArchiveFileDao,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val strategy: ArchivedFileAutoFixStrategy
) {

    private val srcStorageKey = "src_$UT_STORAGE_CREDENTIALS_KEY"
    private val dstStorageKey = "dst_$UT_STORAGE_CREDENTIALS_KEY"

    @BeforeEach
    fun beforeEach() {
        mockRepositoryCommonUtils(dstStorageKey, srcStorageKey)
    }

    @Test
    fun test() {
        val node = mongoTemplate.createNode(archived = true)
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath)

        // node未归档时，无法继续迁移
        val node2 = mongoTemplate.createNode(archived = false, fullPath = "notArchived")
        val failedNode2 = migrateFailedNodeDao.insertFailedNode(node2.fullPath)
        assertFalse(strategy.fix(failedNode2))

        // 源存储文件正在归档中,无法继续迁移
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.ARCHIVING)
        assertFalse(strategy.fix(failedNode))

        // 目标存储文件正在归档中，无法继续迁移
        cleanAndCreateArchiveFile(dstStorageKey, ArchiveStatus.ARCHIVING)
        assertFalse(strategy.fix(failedNode))

        // 无归档文件，无法继续迁移
        archiveFileDao.remove(Query())
        assertFalse(strategy.fix(failedNode))

        // 源存储归档完成，可继续迁移
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.COMPLETED)
        assertTrue(strategy.fix(failedNode))

        // 目标存储归档文件，可继续迁移
        cleanAndCreateArchiveFile(dstStorageKey, ArchiveStatus.COMPLETED)
        assertTrue(strategy.fix(failedNode))
    }

    private fun cleanAndCreateArchiveFile(storageKey: String, status: ArchiveStatus) {
        archiveFileDao.remove(Query())
        archiveFileDao.createArchiveFile(
            UT_SHA256, storageKey, "archive_$UT_STORAGE_CREDENTIALS_KEY", status
        )
    }
}
