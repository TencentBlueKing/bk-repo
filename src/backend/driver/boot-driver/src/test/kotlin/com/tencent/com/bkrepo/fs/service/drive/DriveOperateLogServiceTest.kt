package com.tencent.com.bkrepo.fs.service.drive

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.pojo.log.OpLogListOption
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLogResponse
import com.tencent.bkrepo.common.metadata.service.log.ROperateLogService
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveOperateLogProperties
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogAggregator
import com.tencent.bkrepo.fs.server.service.drive.DriveOperateLogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

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
        val operateLogService = CapturingOperateLogService()
        val properties = DriveOperateLogProperties().apply {
            enabled = false
            flushInterval = Duration.ofMillis(50)
            batchSize = 1
            queueCapacity = 10
        }
        val service = createService(properties, operateLogService)
        service.record(
            type = EventType.DRIVE_BLOCK_READ.name,
            userId = "user",
            clientAddress = "127.0.0.1",
            projectId = "project",
            repoName = "repo",
            resourceKey = "1",
        )
        delay(200)
        assertTrue(operateLogService.savedBatches.isEmpty())
        service.shutdown()
    }

    @Test
    fun `should flush queued logs asynchronously`() = runBlocking {
        val operateLogService = CapturingOperateLogService()
        val properties = DriveOperateLogProperties().apply {
            enabled = true
            aggregation.enabled = false
            flushInterval = Duration.ofMillis(50)
            batchSize = 1
            queueCapacity = 10
        }
        val service = createService(properties, operateLogService)
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
        assertTrue(operateLogService.savedBatches.isNotEmpty())
        val saved = operateLogService.savedBatches.flatten()
        assertEquals(1, saved.size)
        assertEquals(EventType.DRIVE_NODE_CREATE.name, saved.first().type)
        service.shutdown()
    }

    @Test
    fun `should apply queue capacity change dynamically`() = runBlocking {
        val operateLogService = CapturingOperateLogService()
        val properties = DriveOperateLogProperties().apply {
            enabled = true
            aggregation.enabled = false
            flushInterval = Duration.ofMillis(50)
            batchSize = 10
            queueCapacity = 1
        }
        val service = createService(properties, operateLogService)
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
        properties.queueCapacity = 10
        service.record(
            type = EventType.DRIVE_BLOCK_READ.name,
            userId = "user",
            clientAddress = "127.0.0.1",
            projectId = "project",
            repoName = "repo",
            resourceKey = "3",
        )
        delay(500)
        val saved = operateLogService.savedBatches.flatten()
        assertEquals(2, saved.size)
        assertTrue(saved.any { it.resourceKey == "1" })
        assertTrue(saved.any { it.resourceKey == "3" })
        service.shutdown()
    }

    @Test
    fun `should respect disabled type`() {
        val properties = DriveOperateLogProperties().apply {
            disabledTypes = mutableListOf(EventType.DRIVE_BLOCK_READ.name)
        }
        val service = createService(properties, CapturingOperateLogService())
        assertFalse(service.isEnabled(EventType.DRIVE_BLOCK_READ.name))
        assertTrue(service.isEnabled(EventType.DRIVE_BLOCK_WRITE.name))
        service.shutdown()
    }

    private fun createService(
        properties: DriveOperateLogProperties,
        operateLogService: ROperateLogService,
    ): DriveOperateLogService {
        return DriveOperateLogService(
            properties = properties,
            operateLogService = operateLogService,
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

    private class CapturingOperateLogService : ROperateLogService {
        val savedBatches = CopyOnWriteArrayList<List<OperateLog>>()

        override suspend fun saveEventAsync(event: ArtifactEvent, address: String) = Unit

        override suspend fun save(operateLog: OperateLog) {
            save(listOf(operateLog))
        }

        override suspend fun saveAsync(operateLog: OperateLog) = Unit

        override suspend fun save(operateLogs: Collection<OperateLog>) {
            savedBatches.add(operateLogs.toList())
        }

        override suspend fun saveAsync(operateLogs: Collection<OperateLog>) = Unit

        override suspend fun saveEventsAsync(eventList: List<ArtifactEvent>, address: String) = Unit

        override suspend fun listPage(option: OpLogListOption): Page<OperateLog> {
            return Page(0, 0, 0, 0, emptyList())
        }

        override suspend fun page(
            type: String?,
            projectId: String?,
            repoName: String?,
            operator: String?,
            startTime: String?,
            endTime: String?,
            pageNumber: Int,
            pageSize: Int,
        ): Page<OperateLogResponse?> {
            return Page(0, 0, 0, 0, emptyList())
        }
    }
}
