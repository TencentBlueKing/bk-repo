package com.tencent.bkrepo.job.separation.listener

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.pojo.separation.task.SeparationTaskRequest
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.RESTORE
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl.Companion.RESTORE_ARCHIVED
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import java.time.LocalDateTime

@DisplayName("SeparationRecoveryEventConsumer 自动恢复")
class SeparationRecoveryEventConsumerTest {

    private val separationTaskService = mockk<SeparationTaskService>()
    private val dataSeparationConfig = mockk<DataSeparationConfig>()
    private val separationPackageDao = mockk<SeparationPackageDao>()
    private val separationPackageVersionDao = mockk<SeparationPackageVersionDao>()
    private val separationNodeDao = mockk<SeparationNodeDao>()

    private lateinit var consumer: SeparationRecoveryEventConsumer

    private val sepAt = LocalDateTime.of(2024, 3, 1, 23, 59, 59)

    @BeforeEach
    fun setup() {
        consumer = SeparationRecoveryEventConsumer(
            separationTaskService,
            dataSeparationConfig,
            separationPackageDao,
            separationPackageVersionDao,
            separationNodeDao,
        )
        every { dataSeparationConfig.specialRestoreRepos } returns mutableListOf("p/r", "*/*")
    }

    @Test
    @DisplayName("未开启自动恢复时不创建任务")
    fun autoRecoveryDisabled_skips() {
        every { dataSeparationConfig.enableAutoRecovery } returns false
        consumer.accept(wrap(recoveryEvent()))
        verify(exactly = 0) { separationTaskService.createSeparationTask(any()) }
    }

    @Test
    @DisplayName("非 NODE_SEPARATION_RECOVERY 事件忽略")
    fun wrongEventType_skips() {
        every { dataSeparationConfig.enableAutoRecovery } returns true
        val ev = ArtifactEvent(
            EventType.NODE_CREATED,
            "p",
            "r",
            "/a.txt",
            "u",
            mapOf("repoType" to RepositoryType.GENERIC.name),
        )
        consumer.accept(wrap(ev))
        verify(exactly = 0) { separationTaskService.createSeparationTask(any()) }
    }

    @Test
    @DisplayName("GENERIC 冷节点 archived=true 创建 RESTORE_ARCHIVED")
    fun genericArchived_createsRestoreArchivedTask() {
        every { dataSeparationConfig.enableAutoRecovery } returns true
        every { separationTaskService.findDistinctSeparationDate("p", "r") } returns setOf(sepAt)
        val node = separationNode(archived = true)
        every {
            separationNodeDao.findOneByFullPath("p", "r", "/a.txt", sepAt)
        } returns node
        val reqSlot = slot<SeparationTaskRequest>()
        every { separationTaskService.createSeparationTask(capture(reqSlot)) } just runs

        consumer.accept(wrap(recoveryEvent()))

        verify(exactly = 1) { separationTaskService.createSeparationTask(any()) }
        assertEquals(RESTORE_ARCHIVED, reqSlot.captured.type)
    }

    private fun separationNode(archived: Boolean?) = TSeparationNode(
        id = "n1",
        createdBy = "u",
        createdDate = LocalDateTime.now(),
        lastModifiedBy = "u",
        lastModifiedDate = LocalDateTime.now(),
        folder = false,
        path = "/",
        name = "a.txt",
        fullPath = "/a.txt",
        size = 1L,
        archived = archived,
        projectId = "p",
        repoName = "r",
    ).apply { separationDate = sepAt }

    @Test
    @DisplayName("GENERIC 冷节点未归档 创建 RESTORE")
    fun genericNotArchived_createsRestoreTask() {
        every { dataSeparationConfig.enableAutoRecovery } returns true
        every { separationTaskService.findDistinctSeparationDate("p", "r") } returns setOf(sepAt)
        every {
            separationNodeDao.findOneByFullPath("p", "r", "/a.txt", sepAt)
        } returns separationNode(archived = false)
        val reqSlot = slot<SeparationTaskRequest>()
        every { separationTaskService.createSeparationTask(capture(reqSlot)) } just runs

        consumer.accept(wrap(recoveryEvent()))

        verify(exactly = 1) { separationTaskService.createSeparationTask(any()) }
        assertEquals(RESTORE, reqSlot.captured.type)
    }

    @Test
    @DisplayName("仓库不在 specialRestoreRepos 时不创建任务")
    fun repoNotInRestoreList_skips() {
        every { dataSeparationConfig.enableAutoRecovery } returns true
        every { dataSeparationConfig.specialRestoreRepos } returns mutableListOf("other/*")

        consumer.accept(wrap(recoveryEvent()))

        verify(exactly = 0) { separationTaskService.createSeparationTask(any()) }
    }

    private fun recoveryEvent() = ArtifactEvent(
        EventType.NODE_SEPARATION_RECOVERY,
        "p",
        "r",
        "/a.txt",
        "u",
        mapOf("repoType" to RepositoryType.GENERIC.name),
    )

    private fun wrap(event: ArtifactEvent): Message<ArtifactEvent> {
        val m = mockk<Message<ArtifactEvent>>()
        every { m.payload } returns event
        return m
    }
}
