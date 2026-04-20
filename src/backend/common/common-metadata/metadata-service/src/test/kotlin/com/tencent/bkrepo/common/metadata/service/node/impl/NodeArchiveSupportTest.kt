package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.pojo.separation.task.SeparationTaskRequest
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NodeArchiveSupport 降冷联动")
class NodeArchiveSupportTest {

    @Mock
    private lateinit var nodeBaseService: NodeBaseService

    @Mock
    private lateinit var archiveClient: ArchiveClient

    @Mock
    private lateinit var nodeDao: NodeDao

    @Mock
    private lateinit var repositoryDao: RepositoryDao

    @Mock
    private lateinit var dataSeparationConfig: DataSeparationConfig

    @Mock
    private lateinit var separationNodeDao: SeparationNodeDao

    @Mock
    private lateinit var separationTaskService: SeparationTaskService

    private val sepDate = LocalDateTime.of(2024, 3, 1, 0, 0)

    @BeforeEach
    fun setup() {
        whenever(nodeBaseService.nodeDao).thenReturn(nodeDao)
        whenever(nodeBaseService.repositoryDao).thenReturn(repositoryDao)
        whenever(nodeBaseService.aggregateComputeSize(any())).thenReturn(100L)
        whenever(dataSeparationConfig.specialSeparateRepos).thenReturn(mutableListOf("p/r"))
    }

    @Test
    @DisplayName("未注入降冷依赖时不触发冷表逻辑")
    fun restoreNode_withoutSeparationDeps_skipsCold() {
        val support = NodeArchiveSupport(nodeBaseService, archiveClient, null, null, null)
        support.restoreNode(NodeArchiveRequest("p", "r", "/f.txt", "u"))
        verify(nodeDao).updateFirst(any(), any())
        verify(separationTaskService, never()).createSeparationTask(any())
    }

    @Test
    @DisplayName("恢复归档节点时为冷表 archived 节点创建 RESTORE_ARCHIVED 任务")
    fun restoreNode_createsRestoreArchivedTask_forColdArchivedNode() {
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(setOf(sepDate))
        whenever(separationNodeDao.findByQuery(any<Query>(), eq(sepDate))).thenReturn(
            listOf(
                mutableMapOf(
                    TSeparationNode::fullPath.name to "/f.txt",
                    TSeparationNode::archived.name to true,
                ),
            ),
        )
        val support = NodeArchiveSupport(
            nodeBaseService, archiveClient, dataSeparationConfig, separationNodeDao, separationTaskService,
        )
        support.restoreNode(NodeArchiveRequest("p", "r", "/f.txt", "u"))
        val captor = argumentCaptor<SeparationTaskRequest>()
        verify(separationTaskService).createSeparationTask(captor.capture())
        assertEquals(SeparationTaskServiceImpl.RESTORE_ARCHIVED, captor.firstValue.type)
    }

    @Test
    @DisplayName("getArchivableSize 在降冷开启时累加冷表可归档体积")
    fun getArchivableSize_addsColdAggregate_whenEnabled() {
        whenever(dataSeparationConfig.keepDays).thenReturn(Duration.ofDays(30))
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(setOf(sepDate))
        whenever(separationNodeDao.aggregateSizeByQuery(any(), eq(sepDate))).thenReturn(40L)
        val support = NodeArchiveSupport(
            nodeBaseService, archiveClient, dataSeparationConfig, separationNodeDao, separationTaskService,
        )
        assertEquals(140L, support.getArchivableSize("p", "r", days = 7, size = null))
    }
}
