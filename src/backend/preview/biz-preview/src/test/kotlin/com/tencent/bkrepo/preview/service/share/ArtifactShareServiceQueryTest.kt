package com.tencent.bkrepo.preview.service.share

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.token.OrgScope
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.auth.pojo.user.UserOrgMembership
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.model.drive.TDriveNode
import com.tencent.bkrepo.preview.config.ArtifactShareProperties
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.dao.ArtifactShareDao
import com.tencent.bkrepo.preview.dao.ArtifactShareListCursor
import com.tencent.bkrepo.preview.model.TArtifactShare
import com.tencent.bkrepo.preview.pojo.share.AccessibleShareChannel
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareKind
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareResourceType
import com.tencent.bkrepo.preview.pojo.share.ArtifactShareUpsertRequest
import com.tencent.bkrepo.preview.pojo.share.ShareVisibility
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("作品分享列表与权限")
class ArtifactShareServiceQueryTest {

    private val artifactShareDao = mockk<ArtifactShareDao>()
    private val driveShareNodeResolver = mockk<DriveShareNodeResolver>()
    private val temporaryTokenClient = mockk<ServiceTemporaryTokenClient>(relaxed = true)
    private val orgScopeIdMappingService = mockk<OrgScopeIdMappingService>()
    private val serviceUserClient = mockk<ServiceUserClient>()
    private lateinit var service: ArtifactShareService

    @BeforeEach
    fun setUp() {
        service = ArtifactShareService(
            artifactShareDao = artifactShareDao,
            driveShareNodeResolver = driveShareNodeResolver,
            temporaryTokenClient = temporaryTokenClient,
            orgScopeIdMappingService = orgScopeIdMappingService,
            serviceUserClient = serviceUserClient,
            artifactShareProperties = ArtifactShareProperties(),
        )
        every { orgScopeIdMappingService.toStoredIds(any()) } answers {
            firstArg<Collection<String>>().associateWith { it }
        }
        every { orgScopeIdMappingService.toDisplayIds(any()) } answers {
            firstArg<Collection<String>>().associateWith { it }
        }
        every { serviceUserClient.userDeptById(any()) } returns Response(
            0,
            null,
            UserOrgMembership(userId = "bob", scopes = listOf(OrgScope("1", "dept-1"))),
            null,
        )
    }

    @Test
    fun `listMine returns page and next cursor`() {
        val first = record(id = "id-1", createdBy = "alice", lastModifiedDate = TIME_2)
        val extra = record(id = "id-2", createdBy = "alice", lastModifiedDate = TIME_1)
        every {
            artifactShareDao.listMine("alice", "demo", null, 1)
        } returns listOf(first, extra)

        val page = service.listMine("alice", "demo", null, 1)

        assertEquals(1, page.records.size)
        assertEquals("id-1", page.records[0].shareId)
        assertEquals("html", page.records[0].type)
        assertEquals(ArtifactShareListCursor(TIME_2, "id-1").encode(), page.nextCursor)
        assertEquals(1, page.limit)
        assertTrue(page.records[0].userIds.isEmpty())
    }

    @Test
    fun `listAccessible passes user and ancestor department ids`() {
        every { serviceUserClient.userDeptById("bob") } returns Response(
            0,
            null,
            UserOrgMembership(
                userId = "bob",
                scopes = listOf(
                    OrgScope("6", "bg-1"),
                    OrgScope("1", "dept-1"),
                    OrgScope("7", "center-1"),
                ),
            ),
            null,
        )
        val item = record(
            id = "id-9",
            createdBy = "alice",
            visibility = ShareVisibility.CUSTOM,
            userIds = listOf("bob"),
            lastModifiedDate = TIME_2,
            downloadToken = "download-token",
            agentId = "agent-9",
        )
        every {
            artifactShareDao.listAccessible(
                "bob",
                listOf("bg-1", "dept-1", "center-1"),
                null,
                null,
                DEFAULT_PAGE_LIMIT,
                null,
                null,
            )
        } returns listOf(item)

        val page = service.listAccessible("bob", null, null, null)

        assertEquals(1, page.records.size)
        assertEquals("id-9", page.records[0].shareId)
        assertEquals("demo-artifact", page.records[0].artifactName)
        assertEquals("html", page.records[0].type)
        assertEquals("/a.html", page.records[0].fullPath)
        assertEquals("download-token", page.records[0].downloadToken)
        assertEquals("agent-9", page.records[0].agentId)
        assertNull(page.nextCursor)
    }

    @Test
    fun `listAccessible public channel queries public visibility`() {
        every {
            artifactShareDao.listAccessible(
                "bob",
                listOf("dept-1"),
                null,
                null,
                DEFAULT_PAGE_LIMIT,
                AccessibleShareChannel.PUBLIC,
                null,
            )
        } returns emptyList()

        val page = service.listAccessible("bob", null, null, null, "public")

        assertTrue(page.records.isEmpty())
        verify {
            artifactShareDao.listAccessible(
                "bob",
                listOf("dept-1"),
                null,
                null,
                DEFAULT_PAGE_LIMIT,
                AccessibleShareChannel.PUBLIC,
                null,
            )
        }
    }

    @Test
    fun `listAccessible custom channel queries directed shares`() {
        every {
            artifactShareDao.listAccessible(
                "bob",
                listOf("dept-1"),
                null,
                null,
                DEFAULT_PAGE_LIMIT,
                AccessibleShareChannel.CUSTOM,
                null,
            )
        } returns emptyList()

        service.listAccessible("bob", null, null, null, "CUSTOM")

        verify {
            artifactShareDao.listAccessible(
                "bob",
                listOf("dept-1"),
                null,
                null,
                DEFAULT_PAGE_LIMIT,
                AccessibleShareChannel.CUSTOM,
                null,
            )
        }
    }

    @Test
    fun `listAccessible featured filter is independent of permission channel`() {
        every {
            artifactShareDao.listAccessible(
                "bob",
                listOf("dept-1"),
                null,
                null,
                DEFAULT_PAGE_LIMIT,
                AccessibleShareChannel.ALL,
                true,
            )
        } returns emptyList()

        val page = service.listAccessible("bob", null, null, null, "ALL", true)

        assertTrue(page.records.isEmpty())
        verify {
            artifactShareDao.listAccessible(
                "bob",
                listOf("dept-1"),
                null,
                null,
                DEFAULT_PAGE_LIMIT,
                AccessibleShareChannel.ALL,
                true,
            )
        }
    }

    @Test
    fun `listAccessible rejects unknown channel`() {
        val exception = assertThrows(ErrorCodeException::class.java) {
            service.listAccessible("bob", null, null, null, "platform")
        }
        assertEquals(CommonMessageCode.PARAMETER_INVALID, exception.messageCode)
        verify(exactly = 0) { artifactShareDao.listAccessible(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `revoke missing share is idempotent`() {
        every { artifactShareDao.findByShareId("missing") } returns null
        service.revoke("alice", "missing")
        verify(exactly = 0) { artifactShareDao.removeByProjectRepoResourceId(any(), any(), any()) }
    }

    @Test
    fun `revoke by non-owner is forbidden`() {
        every { artifactShareDao.findByShareId("id-1") } returns record(id = "id-1", createdBy = "alice")
        val exception = assertThrows(ErrorCodeException::class.java) {
            service.revoke("bob", "id-1")
        }
        assertEquals(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_FORBIDDEN, exception.messageCode)
        verify(exactly = 0) { artifactShareDao.removeByProjectRepoResourceId(any(), any(), any()) }
    }

    @Test
    fun `upsert update keeps custom artifact name`() {
        stubUpsertNode()
        every { artifactShareDao.findActiveByProjectRepoResourceId("p", "r", 9L) } returns record(
            id = "id-1",
            createdBy = "alice",
            artifactName = "custom-site",
            featured = true,
            previewToken = "old-preview",
            downloadToken = "old-download",
        )
        val saved = slot<TArtifactShare>()
        every { artifactShareDao.save(capture(saved)) } answers { saved.captured }

        service.upsert(
            "alice",
            ArtifactShareUpsertRequest(
                projectId = "p",
                repoName = "r",
                resourceId = 9L,
                visibility = ShareVisibility.PUBLIC,
            ),
        )

        assertEquals("custom-site", saved.captured.artifactName)
        assertEquals(true, saved.captured.featured)
        assertEquals("html", saved.captured.artifactType)
    }

    @Test
    fun `upsert stores artifact type from node metadata`() {
        val node = mockk<TDriveNode>()
        every { node.createdBy } returns "alice"
        every { driveShareNodeResolver.resolveFileByIno("p", "r", 9L) } returns DriveShareNodeInfo(
            node = node,
            fullPath = "/a.html",
        )
        every { driveShareNodeResolver.metadataValue(node, any()) } answers {
            val key = invocation.args[1] as String
            if (key == DriveShareNodeResolver.METADATA_ARTIFACT_TYPE) "table" else null
        }
        every { temporaryTokenClient.createToken(any()) } returnsMany listOf(
            Response(0, null, listOf(tokenInfo("preview-token")), null),
            Response(0, null, listOf(tokenInfo("download-token")), null),
        )
        every { artifactShareDao.findActiveByProjectRepoResourceId("p", "r", 9L) } returns null
        val saved = slot<TArtifactShare>()
        every { artifactShareDao.save(capture(saved)) } answers { saved.captured }

        val result = service.upsert(
            "alice",
            ArtifactShareUpsertRequest(
                projectId = "p",
                repoName = "r",
                resourceId = 9L,
                visibility = ShareVisibility.PUBLIC,
            ),
        )

        assertEquals("table", saved.captured.artifactType)
        assertEquals("table", result.type)
    }

    @Test
    fun `rename updates artifact name without touching tokens`() {
        every { artifactShareDao.findByShareId("id-1") } returns record(
            id = "id-1",
            createdBy = "alice",
            previewToken = "preview-token",
            downloadToken = "download-token",
        )
        val saved = slot<TArtifactShare>()
        every { artifactShareDao.save(capture(saved)) } answers { saved.captured }

        val result = service.rename("alice", "id-1", "  新站点  ")

        assertEquals("新站点", result.artifactName)
        assertEquals("preview-token", saved.captured.previewToken)
        verify(exactly = 0) { temporaryTokenClient.createToken(any()) }
        verify(exactly = 0) { temporaryTokenClient.deleteToken(any()) }
    }

    @Test
    fun `rename blank name is invalid`() {
        val exception = assertThrows(ErrorCodeException::class.java) {
            service.rename("alice", "id-1", "  ")
        }
        assertEquals(CommonMessageCode.PARAMETER_INVALID, exception.messageCode)
        verify(exactly = 0) { artifactShareDao.save(any()) }
    }

    @Test
    fun `rename by non-owner is forbidden`() {
        every { artifactShareDao.findByShareId("id-1") } returns record(id = "id-1", createdBy = "alice")
        val exception = assertThrows(ErrorCodeException::class.java) {
            service.rename("bob", "id-1", "新站点")
        }
        assertEquals(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_FORBIDDEN, exception.messageCode)
        verify(exactly = 0) { artifactShareDao.save(any()) }
    }

    @Test
    fun `rename missing share is not found`() {
        every { artifactShareDao.findByShareId("missing") } returns null
        val exception = assertThrows(ErrorCodeException::class.java) {
            service.rename("alice", "missing", "新站点")
        }
        assertEquals(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_NOT_FOUND, exception.messageCode)
    }

    @Test
    fun `public upsert clears permission arrays`() {
        stubUpsertNode()
        every { artifactShareDao.findActiveByProjectRepoResourceId("p", "r", 9L) } returns null
        val saved = slot<TArtifactShare>()
        every { artifactShareDao.save(capture(saved)) } answers { saved.captured }

        service.upsert(
            "alice",
            ArtifactShareUpsertRequest(
                projectId = "p",
                repoName = "r",
                resourceId = 9L,
                visibility = ShareVisibility.PUBLIC,
                userIds = listOf("bob"),
                orgIds = listOf("dept-1"),
            ),
        )

        assertTrue(saved.captured.userIds.isEmpty())
        assertTrue(saved.captured.orgIds.isEmpty())
    }

    @Test
    fun `custom upsert without targets is rejected`() {
        assertThrows(ErrorCodeException::class.java) {
            service.upsert(
                "alice",
                ArtifactShareUpsertRequest(
                    projectId = "p",
                    repoName = "r",
                    resourceId = 9L,
                    visibility = ShareVisibility.CUSTOM,
                ),
            )
        }
        verify(exactly = 0) { artifactShareDao.save(any()) }
    }

    @Test
    fun `open denies user outside share permission`() {
        every { artifactShareDao.findByShareId("id-1") } returns record(
            id = "id-1",
            createdBy = "alice",
            visibility = ShareVisibility.CUSTOM,
            userIds = listOf("carol"),
            orgIds = listOf("other-dept"),
        )
        val exception = assertThrows(ErrorCodeException::class.java) {
            service.open("bob", "id-1")
        }
        assertEquals(PreviewMessageCode.PREVIEW_ARTIFACT_SHARE_ACCESS_DENIED, exception.messageCode)
    }

    @Test
    fun `open for specified user omits permission ids`() {
        every { artifactShareDao.findByShareId("id-1") } returns record(
            id = "id-1",
            createdBy = "alice",
            visibility = ShareVisibility.CUSTOM,
            userIds = listOf("bob", "carol"),
            orgIds = listOf("dept-1"),
            previewToken = "preview-token",
            downloadToken = "download-token",
        )
        val node = mockk<TDriveNode>()
        every { driveShareNodeResolver.resolveFileByIno("p", "r", 9L) } returns DriveShareNodeInfo(
            node = node,
            fullPath = "/a.html",
        )

        val result = service.open("bob", "id-1")

        assertEquals("id-1", result.share.shareId)
        assertTrue(result.share.userIds.isEmpty())
        assertTrue(result.share.orgIds.isEmpty())
        assertEquals("/ui/p/filePreview/local/0/r/a.html?token=preview-token", result.previewUrl)
        assertEquals(
            "/web/fs-server/drive/temporary/download/p/r/a.html?token=download-token",
            result.downloadUrl,
        )
    }

    @Test
    fun `open utf8-encodes chinese fullPath in preview and download urls`() {
        val chinesePath = "/BKCI介绍/v1/BKCI介绍_v1.html"
        every { artifactShareDao.findByShareId("id-1") } returns record(
            id = "id-1",
            createdBy = "alice",
            previewToken = "preview-token",
            downloadToken = "download-token",
        ).copy(fullPath = chinesePath)
        val node = mockk<TDriveNode>()
        every { driveShareNodeResolver.resolveFileByIno("p", "r", 9L) } returns DriveShareNodeInfo(
            node = node,
            fullPath = chinesePath,
        )

        val result = service.open("alice", "id-1")

        val encodedName = "BKCI%E4%BB%8B%E7%BB%8D"
        assertEquals(
            "/ui/p/filePreview/local/0/r/$encodedName/v1/${encodedName}_v1.html?token=preview-token",
            result.previewUrl,
        )
        assertEquals(
            "/web/fs-server/drive/temporary/download/p/r/$encodedName/v1/${encodedName}_v1.html?token=download-token",
            result.downloadUrl,
        )
    }

    @Test
    fun `invalid cursor is parameter invalid`() {
        val exception = assertThrows(ErrorCodeException::class.java) {
            service.listMine("alice", null, "bad", 20)
        }
        assertEquals(CommonMessageCode.PARAMETER_INVALID, exception.messageCode)
    }

    private fun stubUpsertNode() {
        val node = mockk<TDriveNode>()
        every { node.createdBy } returns "alice"
        every { driveShareNodeResolver.resolveFileByIno("p", "r", 9L) } returns DriveShareNodeInfo(
            node = node,
            fullPath = "/a.html",
        )
        every { driveShareNodeResolver.metadataValue(node, any()) } returns null
        every { temporaryTokenClient.createToken(any()) } returnsMany listOf(
            Response(0, null, listOf(tokenInfo("preview-token")), null),
            Response(0, null, listOf(tokenInfo("download-token")), null),
        )
    }

    private fun record(
        id: String,
        createdBy: String,
        visibility: ShareVisibility = ShareVisibility.PUBLIC,
        userIds: List<String> = emptyList(),
        orgIds: List<String> = emptyList(),
        lastModifiedDate: LocalDateTime = TIME_2,
        artifactName: String? = "demo-artifact",
        featured: Boolean = false,
        previewToken: String? = null,
        downloadToken: String? = null,
        agentId: String? = null,
    ): TArtifactShare {
        return TArtifactShare(
            id = id,
            shareKind = ArtifactShareKind.MATERIAL,
            resourceType = ArtifactShareResourceType.DRIVE_NODE,
            createdBy = createdBy,
            createdDate = lastModifiedDate,
            lastModifiedBy = createdBy,
            lastModifiedDate = lastModifiedDate,
            projectId = "p",
            repoName = "r",
            resourceId = 9L,
            fullPath = "/a.html",
            visibility = visibility,
            userIds = userIds,
            orgIds = orgIds,
            featured = featured,
            agentId = agentId,
            artifactName = artifactName,
            previewToken = previewToken,
            downloadToken = downloadToken,
        )
    }

    private fun tokenInfo(token: String): TemporaryTokenInfo {
        return TemporaryTokenInfo(
            projectId = "p",
            repoName = "r",
            fullPath = "/a.html",
            token = token,
            authorizedUserList = emptySet(),
            authorizedOrgList = emptySet(),
            authorizedIpList = emptySet(),
            expireDate = null,
            permits = null,
            type = TokenType.PREVIEW,
            createdBy = "alice",
        )
    }

    companion object {
        private const val DEFAULT_PAGE_LIMIT = 100
        private val TIME_1 = LocalDateTime.of(2026, 8, 1, 10, 0)
        private val TIME_2 = LocalDateTime.of(2026, 8, 13, 10, 0)
    }
}
