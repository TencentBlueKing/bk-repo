package com.tencent.bkrepo.repository.service.clientupgrade.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.dao.ClientChangelogDao
import com.tencent.bkrepo.repository.model.TClientChangelog
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogListOption
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogStatus
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogUpsertRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime

class ClientChangelogServiceImplTest {

    private val dao: ClientChangelogDao = mockk(relaxed = true)
    private val service = ClientChangelogServiceImpl(dao)

    // ---------- findPublishedEntry ----------

    @Test
    fun `findPublishedEntry rejects blank productId`() {
        val ex = assertThrows(ErrorCodeException::class.java) {
            service.findPublishedEntry(productId = "  ", version = "3.0.2")
        }
        assertEquals(CommonMessageCode.PARAMETER_EMPTY.getCode(), ex.messageCode.getCode())
    }

    @Test
    fun `findPublishedEntry rejects blank version`() {
        assertThrows(ErrorCodeException::class.java) {
            service.findPublishedEntry(productId = "bk_artifacts_ui", version = "")
        }
    }

    @Test
    fun `findPublishedEntry returns null when dao returns null`() {
        every { dao.findPublished(any(), any()) } returns null
        assertNull(
            service.findPublishedEntry(
                productId = "bk_artifacts_ui",
                version = "3.0.2",
            ),
        )
    }

    @Test
    fun `findPublishedEntry maps record body back to entry`() {
        val notes = "### 新增\n- 支持网络拥塞检测"
        val record = sampleRecord(releaseNotes = notes)
        every { dao.findPublished(any(), any()) } returns record

        val entry = service.findPublishedEntry("bk_artifacts_ui", "3.0.2")
        assertNotNull(entry)
        assertEquals("3.0.2", entry!!.version)
        assertEquals("2026-06-16", entry.releasedAt)
        assertEquals(notes, entry.releaseNotes)
    }

    @Test
    fun `findPublishedEntry normalizes productId to lowercase before query`() {
        val pidSlot = slot<String>()
        val verSlot = slot<String>()
        every { dao.findPublished(capture(pidSlot), capture(verSlot)) } returns null

        service.findPublishedEntry(productId = "  BK_Artifacts_UI ", version = "  3.0.2 ")

        assertEquals("bk_artifacts_ui", pidSlot.captured)
        assertEquals("3.0.2", verSlot.captured)
    }

    // ---------- pagePublishedHistory ----------

    @Test
    fun `pagePublishedHistory passes paging and maps records`() {
        val pidSlot = slot<String>()
        val pageNumSlot = slot<Int>()
        val pageSizeSlot = slot<Int>()
        every {
            dao.pagePublished(capture(pidSlot), capture(pageNumSlot), capture(pageSizeSlot))
        } returns Page(
            pageNumber = 1,
            pageSize = 20,
            totalRecords = 1,
            records = listOf(sampleRecord("notes")),
        )

        val page = service.pagePublishedHistory(
            productId = "BK_ARTIFACTS_UI",
            pageNumber = 1,
            pageSize = 20,
        )

        assertEquals("bk_artifacts_ui", pidSlot.captured)
        assertEquals(1, pageNumSlot.captured)
        assertEquals(20, pageSizeSlot.captured)
        assertEquals(1, page.totalRecords)
        assertEquals(1, page.records.size)
        assertEquals("3.0.2", page.records[0].version)
    }

    // ---------- listPage (admin) ----------

    @Test
    fun `listPage maps records to vo`() {
        every { dao.pageByOption(any()) } returns Page(
            pageNumber = 1,
            pageSize = 20,
            totalRecords = 2,
            records = listOf(
                sampleRecord("a", id = "id-a"),
                sampleRecord("b", id = "id-b", status = ClientChangelogStatus.DRAFT),
            ),
        )
        val page = service.listPage(ClientChangelogListOption(pageNumber = 1, pageSize = 20))
        assertEquals(2, page.records.size)
        assertEquals("id-a", page.records[0].id)
        assertEquals(ClientChangelogStatus.PUBLISHED, page.records[0].status)
        assertEquals(ClientChangelogStatus.DRAFT, page.records[1].status)
    }

    // ---------- getById (admin) ----------

    @Test
    fun `getById rejects blank id`() {
        val ex = assertThrows(ErrorCodeException::class.java) {
            service.getById(" ")
        }
        assertEquals(CommonMessageCode.PARAMETER_EMPTY.getCode(), ex.messageCode.getCode())
    }

    @Test
    fun `getById throws when record not found`() {
        every { dao.findById("missing") } returns null
        val ex = assertThrows(ErrorCodeException::class.java) {
            service.getById("missing")
        }
        assertEquals(CommonMessageCode.RESOURCE_NOT_FOUND.getCode(), ex.messageCode.getCode())
    }

    @Test
    fun `getById returns vo when record exists`() {
        every { dao.findById("id-1") } returns sampleRecord("notes", id = "id-1")
        val vo = service.getById("id-1")
        assertEquals("id-1", vo.id)
        assertEquals("3.0.2", vo.version)
        assertEquals("notes", vo.releaseNotes)
    }

    // ---------- getByKey (admin) ----------

    @Test
    fun `getByKey throws when record not found`() {
        every { dao.findByKey(any(), any()) } returns null
        val ex = assertThrows(ErrorCodeException::class.java) {
            service.getByKey("bk_artifacts_ui", "9.9.9")
        }
        assertEquals(CommonMessageCode.RESOURCE_NOT_FOUND.getCode(), ex.messageCode.getCode())
    }

    @Test
    fun `getByKey normalizes key before query`() {
        val pidSlot = slot<String>()
        val verSlot = slot<String>()
        every { dao.findByKey(capture(pidSlot), capture(verSlot)) } returns
            sampleRecord("notes")

        service.getByKey("BK_ARTIFACTS_UI", "  3.0.2 ")

        assertEquals("bk_artifacts_ui", pidSlot.captured)
        assertEquals("3.0.2", verSlot.captured)
    }

    // ---------- upsert validations ----------

    @Test
    fun `upsert rejects blank productId`() {
        assertThrows(ErrorCodeException::class.java) {
            service.upsert("admin", sampleUpsertRequest().copy(productId = " "))
        }
    }

    @Test
    fun `upsert rejects blank version`() {
        assertThrows(ErrorCodeException::class.java) {
            service.upsert("admin", sampleUpsertRequest().copy(version = ""))
        }
    }

    @Test
    fun `upsert rejects blank releasedAt`() {
        assertThrows(ErrorCodeException::class.java) {
            service.upsert("admin", sampleUpsertRequest().copy(releasedAt = ""))
        }
    }

    @Test
    fun `upsert rejects blank releaseNotes`() {
        val ex = assertThrows(ErrorCodeException::class.java) {
            service.upsert("admin", sampleUpsertRequest(releaseNotes = "  \n  "))
        }
        assertEquals(CommonMessageCode.PARAMETER_INVALID.getCode(), ex.messageCode.getCode())
    }

    // ---------- upsert insert ----------

    @Test
    fun `upsert inserts new record when key not exists`() {
        every { dao.findByKey(any(), any()) } returns null
        val captured = slot<TClientChangelog>()
        every { dao.insert(capture(captured)) } answers { captured.captured }

        val request = sampleUpsertRequest()
        val vo = service.upsert("admin", request)

        assertEquals("bk_artifacts_ui", captured.captured.productId)
        assertEquals("3.0.2", captured.captured.version)
        assertEquals("2026-06-16", captured.captured.releasedAt)
        assertEquals(ClientChangelogStatus.DRAFT.name, captured.captured.status)
        assertEquals("admin", captured.captured.createdBy)
        assertEquals("admin", captured.captured.lastModifiedBy)
        assertEquals("### 新增\n- a", captured.captured.releaseNotes)
        assertEquals(ClientChangelogStatus.DRAFT, vo.status)
    }

    @Test
    fun `upsert normalizes productId to lowercase on insert`() {
        every { dao.findByKey(any(), any()) } returns null
        val captured = slot<TClientChangelog>()
        every { dao.insert(capture(captured)) } answers { captured.captured }

        service.upsert("admin", sampleUpsertRequest().copy(productId = "  BK_Artifacts_UI "))

        assertEquals("bk_artifacts_ui", captured.captured.productId)
    }

    @Test
    fun `upsert maps DuplicateKeyException to RESOURCE_EXISTED`() {
        every { dao.findByKey(any(), any()) } returns null
        every { dao.insert(any<TClientChangelog>()) } throws DuplicateKeyException("dup")

        val ex = assertThrows(ErrorCodeException::class.java) {
            service.upsert("admin", sampleUpsertRequest())
        }
        assertEquals(CommonMessageCode.RESOURCE_EXISTED.getCode(), ex.messageCode.getCode())
    }

    // ---------- upsert update ----------

    @Test
    fun `upsert updates existing record matched by key`() {
        val original = sampleRecord(
            releaseNotes = "old",
            id = "id-1",
            createdBy = "alice",
            createdDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        )
        every { dao.findByKey("bk_artifacts_ui", "3.0.2") } returns original
        val savedSlot = slot<TClientChangelog>()
        every { dao.save(capture(savedSlot)) } answers { savedSlot.captured }

        val vo = service.upsert(
            "bob",
            sampleUpsertRequest(releaseNotes = "new").copy(status = ClientChangelogStatus.PUBLISHED),
        )

        // 审计字段：createdBy / createdDate 不变，lastModifiedBy 刷新
        assertEquals("alice", savedSlot.captured.createdBy)
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), savedSlot.captured.createdDate)
        assertEquals("bob", savedSlot.captured.lastModifiedBy)
        assertEquals("new", savedSlot.captured.releaseNotes)
        assertEquals(ClientChangelogStatus.PUBLISHED.name, savedSlot.captured.status)
        assertEquals(ClientChangelogStatus.PUBLISHED, vo.status)
    }

    @Test
    fun `upsert updates existing record matched by id`() {
        val original = sampleRecord(
            releaseNotes = "old",
            id = "id-1",
            createdBy = "alice",
            createdDate = LocalDateTime.of(2026, 1, 1, 0, 0),
        )
        every { dao.findById("id-1") } returns original
        // 同 key 不存在其他记录
        every { dao.findByKey("bk_artifacts_ui", "3.0.2") } returns original
        val savedSlot = slot<TClientChangelog>()
        every { dao.save(capture(savedSlot)) } answers { savedSlot.captured }

        val vo = service.upsert(
            "bob",
            sampleUpsertRequest(releaseNotes = "new").copy(id = "id-1"),
        )

        assertEquals("id-1", vo.id)
        assertEquals("alice", savedSlot.captured.createdBy)
        assertEquals("bob", savedSlot.captured.lastModifiedBy)
        assertEquals("new", savedSlot.captured.releaseNotes)
    }

    @Test
    fun `upsert by id rejects when target key already taken by other record`() {
        val original = sampleRecord("old", id = "id-1", version = "3.0.1")
        val other = sampleRecord("other", id = "id-2", version = "3.0.2")
        every { dao.findById("id-1") } returns original
        every { dao.findByKey("bk_artifacts_ui", "3.0.2") } returns other

        val ex = assertThrows(ErrorCodeException::class.java) {
            service.upsert(
                "bob",
                sampleUpsertRequest().copy(id = "id-1", version = "3.0.2"),
            )
        }
        assertEquals(CommonMessageCode.RESOURCE_EXISTED.getCode(), ex.messageCode.getCode())
    }

    @Test
    fun `upsert by id throws when record not found`() {
        every { dao.findById("missing") } returns null
        val ex = assertThrows(ErrorCodeException::class.java) {
            service.upsert("admin", sampleUpsertRequest().copy(id = "missing"))
        }
        assertEquals(CommonMessageCode.RESOURCE_NOT_FOUND.getCode(), ex.messageCode.getCode())
    }

    @Test
    fun `upsert can rebuild record on same key after physical delete`() {
        // 物理删除后，同 key 再次 upsert 应走 insert 分支，生成全新审计字段
        every { dao.findByKey("bk_artifacts_ui", "3.0.2") } returns null
        val captured = slot<TClientChangelog>()
        every { dao.insert(capture(captured)) } answers { captured.captured }

        val vo = service.upsert("bob", sampleUpsertRequest(releaseNotes = "new"))

        assertEquals("bob", captured.captured.createdBy)
        assertEquals("bob", captured.captured.lastModifiedBy)
        assertEquals("new", vo.releaseNotes)
    }

    // ---------- remove ----------

    @Test
    fun `remove rejects blank id`() {
        assertThrows(ErrorCodeException::class.java) {
            service.remove("admin", "  ")
        }
    }

    @Test
    fun `remove throws when record not found`() {
        every { dao.findById("missing") } returns null
        val ex = assertThrows(ErrorCodeException::class.java) {
            service.remove("admin", "missing")
        }
        assertEquals(CommonMessageCode.RESOURCE_NOT_FOUND.getCode(), ex.messageCode.getCode())
    }

    @Test
    fun `remove invokes physical removeById on success`() {
        every { dao.findById("id-1") } returns sampleRecord("notes", id = "id-1")
        service.remove("admin", "id-1")
        verify(exactly = 1) { dao.removeById(eq("id-1")) }
    }

    // ---------- helpers ----------

    private fun sampleUpsertRequest(
        releaseNotes: String = "### 新增\n- a",
    ) = ClientChangelogUpsertRequest(
        productId = "bk_artifacts_ui",
        version = "3.0.2",
        releasedAt = "2026-06-16",
        status = ClientChangelogStatus.DRAFT,
        releaseNotes = releaseNotes,
    )

    private fun sampleRecord(
        releaseNotes: String,
        id: String? = "id1",
        productId: String = "bk_artifacts_ui",
        version: String = "3.0.2",
        releasedAt: String = "2026-06-16",
        status: ClientChangelogStatus = ClientChangelogStatus.PUBLISHED,
        createdBy: String = "admin",
        createdDate: LocalDateTime = LocalDateTime.now(),
    ) = TClientChangelog(
        id = id,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = createdBy,
        lastModifiedDate = createdDate,
        productId = productId,
        version = version,
        releasedAt = releasedAt,
        status = status.name,
        releaseNotes = releaseNotes,
    )
}
