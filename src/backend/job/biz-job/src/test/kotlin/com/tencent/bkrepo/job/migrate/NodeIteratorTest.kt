package com.tencent.bkrepo.job.migrate

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingDocument
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingKey
import com.tencent.bkrepo.job.UT_MD5
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.UT_USER
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@DisplayName("Node遍历工具测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeIteratorTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
) {

    private val startDate = LocalDateTime.parse("2024-04-15T14:27:08.493", DateTimeFormatter.ISO_DATE_TIME)
    private val totalCount = 101L
    private val beforeStartDateCount = 51L
    private val afterStartDateCount = 50L
    private val migratedCount = 23L
    private val collectionName = "node_1"

    @BeforeAll
    fun beforeAll() {
        createNodes(startDate)
    }

    @Test
    @DisplayName("测试遍历待迁移制品")
    fun testMigratingIterate() {
        val task = createTask(startDate)
        testIterate(task, beforeStartDateCount)
        // continue migrate
        val continueTask = task.copy(migratedCount = migratedCount)
        testIterate(continueTask, beforeStartDateCount, continueTask.migratedCount)
    }

    @Test
    @DisplayName("测试遍历迁移过程中新增的制品")
    fun testCorrectingIterate() {
        val task = createTask(startDate).copy(state = MigrateRepoStorageTaskState.CORRECTING.name)
        testIterate(task, afterStartDateCount)
        // continue correct，correct不支持从上次断点继续，会从头全量遍历
        val continueTask = task.copy(migratedCount = migratedCount)
        testIterate(continueTask, afterStartDateCount)
    }

    private fun testIterate(task: MigrateRepoStorageTask, totalCount: Long, iteratedCount: Long = 0L) {
        val iterator = NodeIterator(task, mongoTemplate, collectionName)
        assertEquals(totalCount, iterator.totalCount())
        var count = iteratedCount
        while (iterator.hasNext()) {
            iterator.next()
            assertEquals(++count, iterator.iteratedCount())
        }
        assertEquals(totalCount, count)
    }

    private fun createTask(now: LocalDateTime = LocalDateTime.now()): MigrateRepoStorageTask {
        return MigrateRepoStorageTask(
            id = "",
            createdBy = UT_USER,
            createdDate = now,
            lastModifiedBy = UT_USER,
            lastModifiedDate = now,
            startDate = now,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            srcStorageKey = "$UT_STORAGE_CREDENTIALS_KEY-src",
            dstStorageKey = "$UT_STORAGE_CREDENTIALS_KEY-dst",
            state = MigrateRepoStorageTaskState.MIGRATING.name,
        )
    }

    private fun createNodes(startDate: LocalDateTime) {
        val before = startDate.minus(Duration.ofDays(1L))
        val after = startDate.plus(Duration.ofDays(1L))
        val nodes = ArrayList<TNode>()

        for (i in 0 until totalCount) {
            nodes.add(
                TNode(
                    id = null,
                    createdDate = if (i % 2 == 0L) before else after,
                    folder = false,
                    projectId = UT_PROJECT_ID,
                    repoName = UT_REPO_NAME,
                    fullPath = "/a/b/c$i.txt",
                    size = i * 100L,
                    sha256 = UT_SHA256,
                    md5 = UT_MD5
                )
            )
        }

        mongoTemplate.insert(nodes, collectionName)
    }

    @ShardingDocument("node")
    private data class TNode(
        var id: String? = null,
        var createdDate: LocalDateTime,
        var folder: Boolean,
        @ShardingKey(count = 1)
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val size: Long,
        val sha256: String,
        val md5: String,
    )
}
