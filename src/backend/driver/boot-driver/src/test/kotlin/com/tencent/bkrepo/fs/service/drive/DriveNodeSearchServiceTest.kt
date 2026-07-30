package com.tencent.bkrepo.fs.service.drive

import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode.Companion.TYPE_FILE
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveMetadataQueryRule
import com.tencent.bkrepo.common.metadata.pojo.drive.DriveNameQueryRule
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.fs.server.config.properties.drive.DriveProperties
import com.tencent.bkrepo.fs.server.repository.drive.RDriveNodeDao
import com.tencent.bkrepo.fs.server.service.drive.DriveNodeService
import com.tencent.bkrepo.fs.server.service.drive.DriveSnapSeqService
import com.tencent.bkrepo.fs.server.utils.DriveNodeRequestValidator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

class DriveNodeSearchServiceTest {

    private val driveNodeDao = mock<RDriveNodeDao>()
    private val driveSnapSeqService = mock<DriveSnapSeqService>()
    private val driveProperties = DriveProperties().apply { listCountLimit = 100 }
    private val driveNodeRequestValidator = mock<DriveNodeRequestValidator>()

    private val service = DriveNodeService(
        driveNodeDao = driveNodeDao,
        driveSnapSeqService = driveSnapSeqService,
        driveProperties = driveProperties,
        driveNodeRequestValidator = driveNodeRequestValidator,
    )

    @Test
    fun `search returns cursor page with hasMore`() = runBlocking {
        val projectId = "p1"
        val repoName = "drive"
        val now = LocalDateTime.of(2026, 7, 27, 10, 0)
        val records = listOf(
            fileNode(projectId, repoName, "a.png", "id-1", now),
            fileNode(projectId, repoName, "b.pdf", "id-2", now.minusHours(1)),
            fileNode(projectId, repoName, "c.html", "id-3", now.minusHours(2)),
        )
        whenever(
            driveNodeDao.searchPage(
                projectId = eq(projectId),
                repoName = eq(repoName),
                pageSize = eq(3),
                name = anyOrNull(),
                metadata = any(),
                lastModifiedDate = anyOrNull(),
                lastId = anyOrNull(),
                direction = eq(Sort.Direction.DESC),
            ),
        ).thenReturn(records)

        val page = service.search(
            projectId = projectId,
            repoName = repoName,
            pageSize = 2,
        )

        assertEquals(2, page.pageSize)
        assertTrue(page.hasMore)
        assertEquals(2, page.records.size)
        assertEquals("a.png", page.records[0].name)
        assertEquals("b.pdf", page.records[1].name)
    }

    @Test
    fun `search passes filters to dao`() = runBlocking {
        whenever(
            driveNodeDao.searchPage(
                projectId = any(),
                repoName = any(),
                pageSize = any(),
                name = anyOrNull(),
                metadata = any(),
                lastModifiedDate = anyOrNull(),
                lastId = anyOrNull(),
                direction = any(),
            ),
        ).thenReturn(emptyList())

        val cursorTime = LocalDateTime.of(2026, 7, 27, 9, 0)
        val name = DriveNameQueryRule(value = "*report*", operation = OperationType.MATCH_I)
        val metadata = listOf(
            DriveMetadataQueryRule(key = "worksCategory", value = listOf("pdf", "image"), operation = OperationType.IN),
            DriveMetadataQueryRule(key = "author", value = "alice", operation = OperationType.EQ),
        )
        service.search(
            projectId = "p1",
            repoName = "drive",
            pageSize = 20,
            name = name,
            metadata = metadata,
            lastModifiedDate = cursorTime,
            lastId = "last-id",
            direction = Sort.Direction.ASC,
        )

        verify(driveNodeDao).searchPage(
            projectId = "p1",
            repoName = "drive",
            pageSize = 21,
            name = name,
            metadata = metadata,
            lastModifiedDate = cursorTime,
            lastId = "last-id",
            direction = Sort.Direction.ASC,
        )
    }

    @Test
    fun `searchCount returns total`() = runBlocking {
        val name = DriveNameQueryRule(value = "*img*", operation = OperationType.MATCH_I)
        val metadata = listOf(
            DriveMetadataQueryRule(key = "worksCategory", value = "image", operation = OperationType.EQ),
        )
        whenever(
            driveNodeDao.searchCount(
                projectId = "p1",
                repoName = "drive",
                name = name,
                metadata = metadata,
            ),
        ).thenReturn(7L)

        val result = service.searchCount(
            projectId = "p1",
            repoName = "drive",
            name = name,
            metadata = metadata,
        )

        assertEquals(7L, result.total)
    }

    @Test
    fun `searchCount uses distinct series count when distinctByMetadataKeys provided`() = runBlocking {
        val metadata = listOf(
            DriveMetadataQueryRule(key = "IMATE_ARTIFACT_TYPE", value = "pdf", operation = OperationType.EQ),
            DriveMetadataQueryRule(key = "IMATE_AGENT_ID", value = "agent-1", operation = OperationType.EQ),
        )
        val distinctKeys = listOf("IMATE_AGENT_ID", "IMATE_CONVERSATION_ID", "IMATE_ARTIFACT_NAME")
        whenever(
            driveNodeDao.searchDistinctSeriesCount(
                projectId = "p1",
                repoName = "drive",
                name = null,
                metadata = metadata,
                distinctByMetadataKeys = distinctKeys,
                latestVersionFilterKey = "IMATE_ARTIFACT_TYPE",
            ),
        ).thenReturn(3L)

        val result = service.searchCount(
            projectId = "p1",
            repoName = "drive",
            metadata = metadata,
            distinctByMetadataKeys = distinctKeys,
        )

        assertEquals(3L, result.total)
        verify(driveNodeDao).searchDistinctSeriesCount(
            projectId = "p1",
            repoName = "drive",
            name = null,
            metadata = metadata,
            distinctByMetadataKeys = distinctKeys,
            latestVersionFilterKey = "IMATE_ARTIFACT_TYPE",
        )
    }

    @Test
    fun `searchCount returns groups when groupByMetadataKey provided with distinct`() = runBlocking {
        val distinctKeys = listOf("IMATE_AGENT_ID", "IMATE_CONVERSATION_ID", "IMATE_ARTIFACT_NAME")
        val groups = listOf(
            RDriveNodeDao.MetadataGroupCount(value = "image", count = 2L),
            RDriveNodeDao.MetadataGroupCount(value = "pdf", count = 3L),
            RDriveNodeDao.MetadataGroupCount(value = null, count = 1L),
        )
        whenever(
            driveNodeDao.searchDistinctSeriesGroupCount(
                projectId = "p1",
                repoName = "drive",
                name = null,
                metadata = emptyList(),
                distinctByMetadataKeys = distinctKeys,
                latestVersionFilterKey = "IMATE_ARTIFACT_TYPE",
                groupByMetadataKey = "IMATE_ARTIFACT_TYPE",
            ),
        ).thenReturn(groups)

        val result = service.searchCount(
            projectId = "p1",
            repoName = "drive",
            distinctByMetadataKeys = distinctKeys,
            groupByMetadataKey = "IMATE_ARTIFACT_TYPE",
        )

        assertEquals(6L, result.total)
        assertEquals(3, result.groups.size)
        assertEquals("image", result.groups[0].value)
        assertEquals(2L, result.groups[0].count)
        assertEquals(null, result.groups[2].value)
        verify(driveNodeDao).searchDistinctSeriesGroupCount(
            projectId = "p1",
            repoName = "drive",
            name = null,
            metadata = emptyList(),
            distinctByMetadataKeys = distinctKeys,
            latestVersionFilterKey = "IMATE_ARTIFACT_TYPE",
            groupByMetadataKey = "IMATE_ARTIFACT_TYPE",
        )
    }

    @Test
    fun `searchCount groups files when groupByMetadataKey provided without distinct`() = runBlocking {
        whenever(
            driveNodeDao.searchGroupByMetadataCount(
                projectId = "p1",
                repoName = "drive",
                name = null,
                metadata = emptyList(),
                groupByMetadataKey = "IMATE_ARTIFACT_TYPE",
            ),
        ).thenReturn(
            listOf(
                RDriveNodeDao.MetadataGroupCount(value = "code", count = 4L),
            ),
        )

        val result = service.searchCount(
            projectId = "p1",
            repoName = "drive",
            groupByMetadataKey = "IMATE_ARTIFACT_TYPE",
        )

        assertEquals(4L, result.total)
        assertEquals(1, result.groups.size)
        assertEquals("code", result.groups[0].value)
    }

    private fun fileNode(
        projectId: String,
        repoName: String,
        name: String,
        id: String,
        createdDate: LocalDateTime,
    ): TDriveNode {
        return TDriveNode(
            id = id,
            createdBy = "u1",
            createdDate = createdDate,
            lastModifiedBy = "u1",
            lastModifiedDate = createdDate,
            mtime = 1L,
            ctime = 1L,
            atime = 1L,
            projectId = projectId,
            repoName = repoName,
            ino = id.hashCode().toLong(),
            parent = 1L,
            name = name,
            size = 10L,
            mode = 33188,
            type = TYPE_FILE,
            nlink = 1,
            uid = 0,
            gid = 0,
            rdev = 0,
            flags = 0,
            metadata = mutableListOf(TMetadata(key = "worksCategory", value = "image")),
        )
    }
}
