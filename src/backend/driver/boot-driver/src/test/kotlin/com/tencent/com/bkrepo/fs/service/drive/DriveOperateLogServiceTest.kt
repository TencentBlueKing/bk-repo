package com.tencent.com.bkrepo.fs.service.drive

import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.model.TOperateLog
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveOperateLogProperties
import com.tencent.bkrepo.fs.server.repository.drive.DriveOperateLogDao
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogAggregator
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

@DisplayName("Drive 操作审计")
class DriveOperateLogServiceTest {

    @Test
    fun `should merge same user operations within batch`() {
        val baseTime = LocalDateTime.now()
        val logs = listOf(
            sampleLog(EventType.DRIVE_BLOCK_READ.name, baseTime),
            sampleLog(EventType.DRIVE_BLOCK_READ.name, baseTime.plusSeconds(1)),
            sampleLog(EventType.DRIVE_BLOCK_WRITE.name, baseTime.plusSeconds(1)),
        )
        val aggregated = DriveOperateLogAggregator.aggregate(
            logs,
            setOf(EventType.DRIVE_BLOCK_READ.name),
        )
        assertEquals(2, aggregated.size)
        val merged = aggregated.first { it.type == EventType.DRIVE_BLOCK_READ.name }
        assertEquals(true, merged.description["merged"])
        assertEquals(2, merged.description["count"])
    }

    @Test
    fun `should skip record when audit disabled`() = runBlocking {
        val savedBatches = CopyOnWriteArrayList<List<TOperateLog>>()
        val dao = createMockDao(savedBatches)
        val properties = DriveOperateLogProperties().apply {
            enabled = false
            flushInterval = Duration.ofMillis(50)
            batchSize = 1
            queueCapacity = 10
        }
        val service = createService(properties, dao)
        service.record(
            type = EventType.DRIVE_BLOCK_READ.name,
            userId = "user",
            clientAddress = "127.0.0.1",
            projectId = "project",
            repoName = "repo",
            resourceKey = "1",
        )
        delay(200)
        assertTrue(savedBatches.isEmpty())
        service.shutdown()
    }

    @Test
    fun `should flush queued logs asynchronously`() = runBlocking {
        val savedBatches = CopyOnWriteArrayList<List<TOperateLog>>()
        val dao = createMockDao(savedBatches)
        val properties = DriveOperateLogProperties().apply {
            enabled = true
            aggregation.enabled = false
            flushInterval = Duration.ofMillis(50)
            batchSize = 1
            queueCapacity = 10
        }
        val service = createService(properties, dao)
        service.record(
            type = EventType.DRIVE_NODE_CREATE.name,
            userId = "user",
            clientAddress = "127.0.0.1",
            projectId = "project",
            repoName = "repo",
            resourceKey = "100",
            description = mapOf("ino" to 100L),
        )
        delay(500)
        assertTrue(savedBatches.isNotEmpty())
        val saved = savedBatches.flatten()
        assertEquals(1, saved.size)
        assertEquals(EventType.DRIVE_NODE_CREATE.name, saved.first().type)
        service.shutdown()
    }

    @Test
    fun `should apply queue capacity increase dynamically`() = runBlocking {
        val savedBatches = CopyOnWriteArrayList<List<TOperateLog>>()
        val dao = createMockDao(savedBatches)
        val properties = DriveOperateLogProperties().apply {
            enabled = true
            aggregation.enabled = false
            flushInterval = Duration.ofMillis(50)
            batchSize = 1
            queueCapacity = 0
        }
        val service = createService(properties, dao)
        service.record(
            type = EventType.DRIVE_BLOCK_READ.name,
            userId = "user",
            clientAddress = "127.0.0.1",
            projectId = "project",
            repoName = "repo",
            resourceKey = "1",
        )
        service.record(
            type = EventType.DRIVE_BLOCK_READ.name,
            userId = "user",
            clientAddress = "127.0.0.1",
            projectId = "project",
            repoName = "repo",
            resourceKey = "2",
        )
        delay(100.milliseconds)
        properties.queueCapacity = 10
        service.record(
            type = EventType.DRIVE_BLOCK_READ.name,
            userId = "user",
            clientAddress = "127.0.0.1",
            projectId = "project",
            repoName = "repo",
            resourceKey = "3",
        )
        delay(500.milliseconds)
        val saved = savedBatches.flatten()
        assertTrue(saved.any { it.resourceKey == "3" })
        service.shutdown()
    }

    @Test
    fun `should not block caller when waiting for queue slot`() {
        val savedBatches = CopyOnWriteArrayList<List<TOperateLog>>()
        val dao = createMockDao(savedBatches)
        val properties = DriveOperateLogProperties().apply {
            enabled = true
            overflowStrategy = "BLOCK"
            queueCapacity = 0
            queueBlockRetryInterval = Duration.ofSeconds(1)
        }
        val service = createService(properties, dao)
        try {
            assertTimeout(Duration.ofMillis(50)) {
                service.record(
                    type = EventType.DRIVE_BLOCK_READ.name,
                    userId = "user",
                    clientAddress = "127.0.0.1",
                    projectId = "project",
                    repoName = "repo",
                    resourceKey = "1",
                )
            }
            assertTrue(savedBatches.isEmpty())
        } finally {
            service.shutdown()
        }
    }

    @Test
    fun `should respect disabled type`() {
        val properties = DriveOperateLogProperties().apply {
            disabledTypes = mutableListOf(EventType.DRIVE_BLOCK_READ.name)
        }
        val dao = createMockDao(CopyOnWriteArrayList())
        val service = createService(properties, dao)
        assertFalse(service.isEnabled(EventType.DRIVE_BLOCK_READ.name))
        assertTrue(service.isEnabled(EventType.DRIVE_BLOCK_WRITE.name))
        service.shutdown()
    }

    private fun createMockDao(savedBatches: CopyOnWriteArrayList<List<TOperateLog>>): DriveOperateLogDao {
        val dao = mock<DriveOperateLogDao>()
        whenever(runBlocking { dao.insert(any<Collection<TOperateLog>>()) }).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val logs = invocation.getArgument<Collection<TOperateLog>>(0)
            savedBatches.add(logs.toList())
            logs
        }
        return dao
    }

    private fun createService(
        properties: DriveOperateLogProperties,
        operateLogDao: DriveOperateLogDao,
    ): DriveOperateLogService {
        return DriveOperateLogService(
            properties = properties,
            operateLogDao = operateLogDao,
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        )
    }

    private fun sampleLog(type: String, createdDate: LocalDateTime): OperateLog {
        return OperateLog(
            createdDate = createdDate,
            type = type,
            projectId = "project",
            repoName = "repo",
            resourceKey = "1",
            userId = "user",
            clientAddress = "127.0.0.1",
            description = emptyMap(),
        )
    }
}
